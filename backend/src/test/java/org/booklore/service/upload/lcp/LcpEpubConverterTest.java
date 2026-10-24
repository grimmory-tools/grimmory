package org.booklore.service.upload.lcp;

import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LcpEpubConverterTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LcpEpubConverter converter = new LcpEpubConverter(mapper);
    static final String PASSWORD = "library-card";
    static final byte[] CONTENT_KEY = new byte[32];
    static final byte[] CHAPTER = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Original fixture text: español.</p></body></html>".getBytes(StandardCharsets.UTF_8);

    static byte[] encrypt(byte[] input, byte[] key) throws Exception {
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 17);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(iv);
        result.write(cipher.doFinal(input));
        return result.toByteArray();
    }

    byte[] license(boolean profileOne) throws Exception {
        byte[] key = profileOne ? HexFormat.of().parseHex("6eb8897f61be14ec266521ddb05b2fe374f528c4acf851f67e1f3cc5ecca6ca7")
                : LcpEpubConverter.deriveUserKey(PASSWORD);
        return mapper.writeValueAsBytes(Map.of("id", "fixture-license", "encryption", Map.of(
                "profile", profileOne ? "http://readium.org/lcp/profile-1.0" : "http://readium.org/lcp/basic-profile",
                "content_key", Map.of("algorithm", "http://www.w3.org/2001/04/xmlenc#aes256-cbc", "encrypted_value", Base64.getEncoder().encodeToString(encrypt(CONTENT_KEY, key))),
                "user_key", Map.of("algorithm", "http://www.w3.org/2001/04/xmlenc#sha256", "key_check", Base64.getEncoder().encodeToString(encrypt("fixture-license".getBytes(StandardCharsets.UTF_8), key)))),
                "links", new Object[]{Map.of("rel", "publication", "href", "https://example.org/book.epub")}));
    }

    Path publication(boolean compressed, boolean fontObfuscation, boolean brokenSpine) throws Exception {
        String chapterName = "OPS/chapter+one.xhtml";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
        entries.put("META-INF/container.xml", "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".getBytes(StandardCharsets.UTF_8));
        String opf = "<package xmlns=\"http://www.idpf.org/2007/opf\"><manifest><item id=\"chapter\" href=\"chapter+one.xhtml\"/></manifest><spine><itemref idref=\"" + (brokenSpine ? "missing" : "chapter") + "\"/></spine></package>";
        entries.put("OPS/book.opf", opf.getBytes(StandardCharsets.UTF_8));
        byte[] plain = CHAPTER;
        if (compressed) {
            Deflater deflater = new Deflater(6, true);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (var stream = new DeflaterOutputStream(buffer, deflater)) { stream.write(plain); }
            finally { deflater.end(); }
            plain = buffer.toByteArray();
        }
        entries.put(chapterName, encrypt(plain, CONTENT_KEY));
        String enc = "<encryption xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\" xmlns:e=\"http://www.w3.org/2001/04/xmlenc#\"><e:EncryptedData><e:EncryptionMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#aes256-cbc\"/><e:CipherData><e:CipherReference URI=\"" + chapterName + "\"/></e:CipherData><e:EncryptionProperties><e:EncryptionProperty><Compression xmlns=\"http://www.idpf.org/2016/encryption#compression\" Method=\"" + (compressed ? 8 : 0) + "\" OriginalLength=\"" + CHAPTER.length + "\"/></e:EncryptionProperty></e:EncryptionProperties></e:EncryptedData>";
        if (fontObfuscation) {
            enc += "<e:EncryptedData><e:EncryptionMethod Algorithm=\"http://www.idpf.org/2008/embedding\"/><e:CipherData><e:CipherReference URI=\"OPS/font.otf\"/></e:CipherData></e:EncryptedData>";
            entries.put("OPS/font.otf", new byte[]{1, 2, 3});
        }
        entries.put("META-INF/encryption.xml", (enc + "</encryption>").getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/license.lcpl", license(false));
        Path path = Files.createTempFile(directory, "fixture-", ".epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return path;
    }

    @Test
    void unlocksBasicAndProfileOneWithKnownPassword() throws Exception {
        for (boolean profileOne : new boolean[]{false, true}) {
            var license = converter.readLicense(license(profileOne));
            assertThat(converter.unlock(license, LcpEpubConverter.deriveUserKey(PASSWORD))).isEqualTo(CONTENT_KEY);
            assertThat(license.toString()).doesNotContain(license.encryptedContentKey());
        }
    }

    @Test
    void rejectsIncorrectAndMalformedLicensesBeforeDownload() throws Exception {
        var license = converter.readLicense(license(true));
        assertThatThrownBy(() -> converter.unlock(license, LcpEpubConverter.deriveUserKey("incorrect")))
                .isInstanceOf(APIException.class).hasMessageContaining("does not match");
        assertThatThrownBy(() -> converter.readLicense("not-json".getBytes(StandardCharsets.UTF_8))).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> converter.readLicense(new byte[LcpEpubConverter.MAX_LICENSE_BYTES + 1])).isInstanceOf(APIException.class);
    }

    @Test
    void exportsCompressedAndUncompressedResourcesAndRemovesLicense() throws Exception {
        for (boolean compressed : new boolean[]{true, false}) {
            Path output = directory.resolve("result.epub");
            converter.convert(publication(compressed, false, false), output, CONTENT_KEY, 1024 * 1024);
            try (ZipFile zip = new ZipFile(output.toFile())) {
                assertThat(zip.getInputStream(zip.getEntry("OPS/chapter+one.xhtml")).readAllBytes()).isEqualTo(CHAPTER);
                assertThat(zip.getEntry("META-INF/license.lcpl")).isNull();
                assertThat(zip.getEntry("META-INF/encryption.xml")).isNull();
                assertThat(zip.entries().nextElement().getName()).isEqualTo("mimetype");
                assertThat(zip.getEntry("mimetype").getMethod()).isEqualTo(ZipEntry.STORED);
            }
        }
    }

    @Test
    void keepsFontObfuscationWithoutKeepingContentEncryption() throws Exception {
        Path output = directory.resolve("font.epub");
        converter.convert(publication(true, true, false), output, CONTENT_KEY, 1024 * 1024);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            String enc = new String(zip.getInputStream(zip.getEntry("META-INF/encryption.xml")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(enc).contains("http://www.idpf.org/2008/embedding").doesNotContain("aes256");
            assertThat(zip.getInputStream(zip.getEntry("OPS/font.otf")).readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void rejectsBrokenReadingOrderAndResourceLimits() throws Exception {
        Path broken = publication(true, false, true);
        assertThatThrownBy(() -> converter.convert(broken, directory.resolve("broken.epub"), CONTENT_KEY, 1024 * 1024))
                .isInstanceOf(APIException.class).hasMessageContaining("reading order");
        Path valid = publication(true, false, false);
        assertThatThrownBy(() -> converter.convert(valid, directory.resolve("small.epub"), CONTENT_KEY, 16))
                .isInstanceOf(APIException.class).hasMessageContaining("size limit");
    }

    @Test
    void preservesLiteralPlusAndRejectsUnsafeResourcePaths() {
        assertThat(LcpEpubConverter.resourcePath("OPS/", "chapter+one.xhtml#test")).isEqualTo("OPS/chapter+one.xhtml");
        assertThat(LcpEpubConverter.resourcePath("OPS/", "../cover%20page.xhtml")).isEqualTo("cover page.xhtml");
        assertThatThrownBy(() -> LcpEpubConverter.resourcePath("", "../escape")).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> LcpEpubConverter.resourcePath("", "https://example.org/resource")).isInstanceOf(APIException.class);
    }
}
