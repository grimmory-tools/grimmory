package org.booklore.service.upload.lcp;

import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.util.SecureXmlUtils;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Locale;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** LCP resource layout follows the Readium LCP specification. */
@Component
@RequiredArgsConstructor
public class LcpEpubConverter {
    static final int MAX_LICENSE_BYTES = 1024 * 1024;
    private static final int MAX_XML_BYTES = 16 * 1024 * 1024;
    private static final String AES = "http://www.w3.org/2001/04/xmlenc#aes256-cbc";
    private static final String ENC = "http://www.w3.org/2001/04/xmlenc#";
    private static final String COMPRESSION = "http://www.idpf.org/2016/encryption#compression";
    private static final Set<String> FONT_OBFUSCATION = Set.of("http://www.idpf.org/2008/embedding", "http://ns.adobe.com/pdf/enc#RC");
    // Profile 1.0 transformation follows the original NoDRM LCP implementation.
    private static final byte[] PROFILE_CONSTANT = HexFormat.of().parseHex(
            "b3a07c4d42880e69398e05392405050efeea0664c0b638b7c986556fa9b58d77b31a40eb6a4fdba1e4537229d9f779daad1cc41ee968153cb71f27dc9696d40f");
    private final ObjectMapper objectMapper;

    public record License(String id, String profile, String keyCheck, String encryptedContentKey, URI publication) {
        @Override
        public String toString() { return "LCP license [redacted]"; }
    }

    private record Resource(boolean compressed, long originalLength) { }

    public License readLicense(byte[] data) {
        require(data.length <= MAX_LICENSE_BYTES, "LCP license exceeds the size limit.");
        try {
            JsonNode root = objectMapper.readTree(data);
            require(AES.equals(root.at("/encryption/content_key/algorithm").asText())
                    && "http://www.w3.org/2001/04/xmlenc#sha256".equals(root.at("/encryption/user_key/algorithm").asText()),
                    "Unsupported LCP encryption algorithm.");
            URI publication = null;
            for (JsonNode link : root.path("links")) {
                if ("publication".equals(link.path("rel").asText())) {
                    require(publication == null, "LCP license contains multiple publication links.");
                    publication = URI.create(link.path("href").asText());
                }
            }
            require(publication != null, "LCP license has no publication download link.");
            return new License(text(root, "/id"), text(root, "/encryption/profile"),
                    text(root, "/encryption/user_key/key_check"), text(root, "/encryption/content_key/encrypted_value"), publication);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw ApiError.INVALID_INPUT.createException("Invalid LCP license.");
        }
    }

    private String text(JsonNode root, String pointer) {
        String value = root.at(pointer).asText();
        require(value != null && !value.isEmpty(), "Incomplete LCP license.");
        return value;
    }

    /** Persist only this derived key, never the original passphrase. */
    public static byte[] deriveUserKey(String passphrase) {
        require(passphrase != null && !passphrase.isEmpty() && passphrase.length() <= 1024, "Enter an LCP unlock code (up to 1024 characters).");
        return sha256(passphrase.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] unlock(License license, byte[] userKey) {
        byte[] key = userKey.clone();
        try {
            switch (license.profile()) {
                case "http://readium.org/lcp/basic-profile" -> { }
                case "http://readium.org/lcp/profile-1.0" -> {
                    for (byte value : PROFILE_CONSTANT) {
                        byte[] input = Arrays.copyOf(key, 33);
                        input[32] = value;
                        Arrays.fill(key, (byte) 0);
                        key = sha256(input);
                        Arrays.fill(input, (byte) 0);
                    }
                }
                default -> throw ApiError.INVALID_INPUT.createException("Unsupported LCP encryption profile.");
            }
            byte[] check = decrypt(Base64.getDecoder().decode(license.keyCheck()), key);
            if (!MessageDigest.isEqual(check, license.id().getBytes(StandardCharsets.UTF_8))) {
                throw ApiError.LCP_PASSPHRASE_INCORRECT.createException();
            }
            byte[] contentKey = decrypt(Base64.getDecoder().decode(license.encryptedContentKey()), key);
            require(contentKey.length == 32, "Invalid LCP content key.");
            return contentKey;
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw ApiError.LCP_PASSPHRASE_INCORRECT.createException();
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] sha256(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private byte[] decrypt(byte[] input, byte[] key) throws GeneralSecurityException {
        if (input.length < 32 || input.length % 16 != 0) throw new GeneralSecurityException("Invalid encrypted resource");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(input, 0, 16));
        byte[] plain = cipher.doFinal(input, 16, input.length - 16);
        int padding = Byte.toUnsignedInt(plain[plain.length - 1]);
        if (padding < 1 || padding > 16) throw new GeneralSecurityException("Invalid resource padding");
        return Arrays.copyOf(plain, plain.length - padding);
    }

    public void convert(Path source, Path output, byte[] contentKey, long maxBytes) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            require(zip.size() <= 10_000, "LCP publication contains too many resources.");
            Document encryption = parse(readEntry(zip, "META-INF/encryption.xml", MAX_XML_BYTES));
            Map<String, Resource> resources = new HashMap<>();
            var nodes = encryption.getElementsByTagNameNS(ENC, "EncryptedData");
            for (int i = nodes.getLength() - 1; i >= 0; i--) {
                Element item = (Element) nodes.item(i);
                Element method = first(item, ENC, "EncryptionMethod");
                String algorithm = method.getAttribute("Algorithm");
                if (FONT_OBFUSCATION.contains(algorithm)) continue;
                require(AES.equals(algorithm), "Publication includes unsupported encryption.");
                String name = resourcePath("", first(item, ENC, "CipherReference").getAttribute("URI"));
                require(zip.getEntry(name) != null && !resources.containsKey(name), "Invalid encrypted resource list.");
                var compression = item.getElementsByTagNameNS(COMPRESSION, "Compression");
                boolean compressed = false;
                long originalLength = -1;
                if (compression.getLength() > 0) {
                    Element value = (Element) compression.item(0);
                    String mode = value.getAttribute("Method");
                    require("0".equals(mode) || "8".equals(mode), "Unsupported LCP resource compression.");
                    compressed = "8".equals(mode);
                    try { originalLength = Long.parseLong(value.getAttribute("OriginalLength")); }
                    catch (NumberFormatException exception) { throw ApiError.INVALID_INPUT.createException("Invalid original resource length."); }
                    require(originalLength >= 0 && originalLength <= maxBytes, "LCP resource exceeds the size limit.");
                }
                resources.put(name, new Resource(compressed, originalLength));
                item.getParentNode().removeChild(item);
            }
            require(!resources.isEmpty(), "Publication has no LCP encrypted resources.");
            try (ZipOutputStream result = new ZipOutputStream(Files.newOutputStream(output))) {
                byte[] mime = readEntry(zip, "mimetype", 64);
                require(Arrays.equals(mime, "application/epub+zip".getBytes(StandardCharsets.US_ASCII)), "LCP publication is not an EPUB.");
                writeEntry(result, "mimetype", mime, true);
                var entries = zip.entries();
                Set<String> seen = new HashSet<>();
                long expandedSize = 0;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    require(seen.add(name), "Publication contains duplicate resources.");
                    if (name.equals("mimetype") || name.equals("META-INF/license.lcpl")) continue;
                    byte[] data;
                    if (name.equals("META-INF/encryption.xml")) {
                        if (encryption.getElementsByTagNameNS(ENC, "EncryptedData").getLength() == 0) continue;
                        data = serialize(encryption);
                    } else data = readEntry(zip, name, maxBytes);
                    Resource resource = resources.get(name);
                    if (resource != null) {
                        data = decrypt(data, contentKey);
                        if (resource.compressed()) {
                            Inflater inflater = new Inflater(true);
                            try (var stream = new InflaterInputStream(new ByteArrayInputStream(data), inflater)) {
                                data = readBounded(stream, maxBytes);
                            } finally { inflater.end(); }
                        }
                        require(resource.originalLength() < 0 || resource.originalLength() == data.length, "Decrypted resource has an invalid length.");
                    }
                    expandedSize += data.length;
                    require(expandedSize <= maxBytes * 4, "Expanded publication exceeds the size limit.");
                    writeEntry(result, name, data, false);
                }
            }
        } catch (GeneralSecurityException exception) {
            throw ApiError.INVALID_INPUT.createException("LCP publication could not be decrypted.");
        }
        validate(output, maxBytes);
        require(Files.size(output) <= maxBytes, "Converted EPUB exceeds the upload size limit.");
    }

    private void validate(Path output, long maxBytes) throws IOException {
        try (ZipFile zip = new ZipFile(output.toFile())) {
            Document container = parse(readEntry(zip, "META-INF/container.xml", MAX_XML_BYTES));
            var rootfiles = container.getElementsByTagNameNS("*", "rootfile");
            require(rootfiles.getLength() > 0, "EPUB container has no package document.");
            String packagePath = resourcePath("", ((Element) rootfiles.item(0)).getAttribute("full-path"));
            Document opf = parse(readEntry(zip, packagePath, MAX_XML_BYTES));
            String base = packagePath.contains("/") ? packagePath.substring(0, packagePath.lastIndexOf('/') + 1) : "";
            Set<String> ids = new HashSet<>();
            var manifest = opf.getElementsByTagNameNS("*", "manifest");
            require(manifest.getLength() == 1, "EPUB has no resource manifest.");
            var items = ((Element) manifest.item(0)).getElementsByTagNameNS("*", "item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                require(!item.getAttribute("id").isEmpty() && ids.add(item.getAttribute("id")), "Invalid EPUB resource ID.");
                String href = item.getAttribute("href");
                if (!URI.create(href.replace(" ", "%20")).isAbsolute()) {
                    require(zip.getEntry(resourcePath(base, href)) != null, "EPUB is missing a manifest resource.");
                }
            }
            var spine = opf.getElementsByTagNameNS("*", "itemref");
            require(spine.getLength() > 0, "EPUB has no reading order.");
            for (int i = 0; i < spine.getLength(); i++) require(ids.contains(((Element) spine.item(i)).getAttribute("idref")), "Invalid EPUB reading order.");
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".xhtml")) {
                    parse(readEntry(zip, entry.getName(), Math.min(maxBytes, MAX_XML_BYTES)));
                }
            }
        }
    }

    static byte[] readBounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            require(total <= limit, "LCP file or resource exceeds the size limit.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private byte[] readEntry(ZipFile zip, String name, long limit) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        require(entry != null && entry.getSize() <= limit, "EPUB is missing a resource or exceeds the size limit.");
        try (InputStream stream = zip.getInputStream(entry)) {
            byte[] data = readBounded(stream, limit);
            CRC32 crc = new CRC32();
            crc.update(data);
            require(entry.getCrc() == crc.getValue(), "EPUB contains a damaged resource.");
            return data;
        }
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] data, boolean stored) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (stored) {
            CRC32 crc = new CRC32();
            crc.update(data);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private Document parse(byte[] xml) {
        try { return SecureXmlUtils.createSecureDocumentBuilder(true).parse(new ByteArrayInputStream(xml)); }
        catch (Exception exception) { throw ApiError.INVALID_INPUT.createException("Invalid publication XML."); }
    }

    private byte[] serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            factory.newTransformer().transform(new DOMSource(document), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception exception) { throw ApiError.INVALID_INPUT.createException("Could not write publication encryption metadata."); }
    }

    private Element first(Element element, String namespace, String name) {
        var values = element.getElementsByTagNameNS(namespace, name);
        require(values.getLength() == 1, "Incomplete LCP encryption metadata.");
        return (Element) values.item(0);
    }

    static String resourcePath(String base, String relative) {
        require(relative != null && !relative.isEmpty(), "Empty EPUB resource path.");
        String path = URLDecoder.decode(relative.split("#", 2)[0].replace("+", "%2B"), StandardCharsets.UTF_8);
        require(!path.contains(":") && !path.contains("\\") && !path.startsWith("/"), "Invalid EPUB resource path.");
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : (base + path).split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                require(!parts.isEmpty(), "Invalid EPUB resource path.");
                parts.removeLast();
            } else parts.add(part);
        }
        return String.join("/", parts);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw ApiError.INVALID_INPUT.createException(message);
    }
}
