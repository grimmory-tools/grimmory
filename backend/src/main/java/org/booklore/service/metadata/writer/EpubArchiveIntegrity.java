package org.booklore.service.metadata.writer;

import org.booklore.util.SecureXmlUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class EpubArchiveIntegrity {
    private static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_CONTAINER_BYTES = 1024L * 1024;
    private static final long MAX_PACKAGE_BYTES = 8L * 1024 * 1024;
    private static final byte[] MIMETYPE = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);

    private EpubArchiveIntegrity() {
    }

    static String sha256(Path path) throws IOException {
        BasicFileAttributes before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > MAX_EXPANDED_BYTES) {
            throw new IOException("EPUB is not a regular file or exceeds the size limit");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(
                    Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), digest)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > before.size() || total > MAX_EXPANDED_BYTES) {
                        throw new IOException("EPUB changed while calculating its hash");
                    }
                }
                if (total != before.size()) {
                    throw new IOException("EPUB changed while calculating its hash");
                }
            }
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile() || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                throw new IOException("EPUB changed while calculating its hash");
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    static String validate(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("EPUB must be a regular file");
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            Set<String> names = new HashSet<>();
            long expanded = 0;
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (names.size() >= MAX_ENTRIES || !safeName(entry.getName()) || !names.add(entry.getName())) {
                    throw new IOException("EPUB contains invalid or duplicate archive entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getSize() < 0 || entry.getSize() > MAX_EXPANDED_BYTES - expanded) {
                    throw new IOException("EPUB archive exceeds its expansion limit");
                }
                CRC32 checksum = new CRC32();
                long size = 0;
                try (InputStream input = zip.getInputStream(entry)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        size += count;
                        if (size > entry.getSize() || size > MAX_EXPANDED_BYTES - expanded) {
                            throw new IOException("EPUB archive exceeds its declared entry size");
                        }
                        checksum.update(buffer, 0, count);
                    }
                }
                if (size != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                    throw new IOException("EPUB archive entry failed its integrity check");
                }
                expanded += size;
            }

            ZipEntry mimetype = zip.getEntry("mimetype");
            if (mimetype == null || mimetype.isDirectory() || mimetype.getSize() != MIMETYPE.length) {
                throw new IOException("EPUB mimetype is missing or invalid");
            }
            try (InputStream input = zip.getInputStream(mimetype)) {
                if (!Arrays.equals(input.readAllBytes(), MIMETYPE)) {
                    throw new IOException("EPUB mimetype is missing or invalid");
                }
            }

            ZipEntry container = boundedEntry(zip, "META-INF/container.xml", MAX_CONTAINER_BYTES);
            NodeList rootfiles;
            try (InputStream input = zip.getInputStream(container)) {
                rootfiles = SecureXmlUtils.createSecureDocumentBuilder(true).parse(input)
                        .getElementsByTagNameNS("*", "rootfile");
            }
            if (rootfiles.getLength() == 0) {
                throw new IOException("EPUB package path is missing");
            }
            String packagePath = ((Element) rootfiles.item(0)).getAttribute("full-path");
            if (!safeName(packagePath)) {
                throw new IOException("EPUB package path is invalid");
            }
            ZipEntry epubPackage = boundedEntry(zip, packagePath, MAX_PACKAGE_BYTES);
            try (InputStream input = zip.getInputStream(epubPackage)) {
                var document = SecureXmlUtils.createSecureDocumentBuilder(true).parse(input);
                if (document.getElementsByTagNameNS("*", "metadata").getLength() != 1) {
                    throw new IOException("EPUB package metadata is invalid");
                }
            }
            return packagePath;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("EPUB XML is invalid", e);
        }
    }

    static void validateMetadataRewrite(Path original, Path candidate) throws IOException {
        String originalPackage = validate(original);
        String candidatePackage = validate(candidate);
        if (!originalPackage.equals(candidatePackage)) {
            throw new IOException("EPUB package path changed during metadata update");
        }
        try (ZipFile before = new ZipFile(original.toFile()); ZipFile after = new ZipFile(candidate.toFile())) {
            Set<String> beforeFiles = fileNames(before);
            Set<String> afterFiles = fileNames(after);
            if (!beforeFiles.equals(afterFiles)) {
                throw new IOException("EPUB file entries changed during metadata update");
            }
            for (String name : beforeFiles) {
                if (!name.equals(originalPackage) && !MessageDigest.isEqual(
                        digest(before, before.getEntry(name)), digest(after, after.getEntry(name)))) {
                    throw new IOException("EPUB non-metadata content changed during metadata update: " + name);
                }
            }
            validatePackageStructure(before, after, originalPackage);
        }
    }

    private static void validatePackageStructure(ZipFile before, ZipFile after, String packagePath) throws IOException {
        try (InputStream oldInput = before.getInputStream(before.getEntry(packagePath));
             InputStream newInput = after.getInputStream(after.getEntry(packagePath))) {
            var builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            Element oldRoot = builder.parse(oldInput).getDocumentElement();
            Element newRoot = builder.parse(newInput).getDocumentElement();
            String oldPrefix = oldRoot.getAttribute("prefix").strip();
            String newPrefix = newRoot.getAttribute("prefix").strip();
            String allowedPrefix = oldPrefix.isEmpty()
                    ? "booklore: http://booklore.org/metadata/1.0/"
                    : oldPrefix + " booklore: http://booklore.org/metadata/1.0/";
            if (!newPrefix.equals(oldPrefix) && !newPrefix.equals(allowedPrefix)) {
                throw new IOException("EPUB package prefix changed during metadata update");
            }
            if (!snapshot(oldRoot, true).equals(snapshot(newRoot, true))) {
                throw new IOException("EPUB package content outside metadata changed during metadata update");
            }
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("EPUB XML is invalid", e);
        }
    }

    private static ElementSnapshot snapshot(Element element, boolean root) {
        Map<String, String> attributes = new TreeMap<>();
        var nodes = element.getAttributes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node attribute = nodes.item(i);
            if (!"http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())
                    && !(root && attribute.getNamespaceURI() == null && "prefix".equals(attribute.getNodeName()))) {
                attributes.put("{" + attribute.getNamespaceURI() + "}" + attribute.getNodeName(), attribute.getNodeValue());
            }
        }
        List<Object> children = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                if (root && "metadata".equals(child.getLocalName())) {
                    children.add("metadata");
                } else {
                    children.add(snapshot(child, false));
                }
            } else if ((node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !node.getNodeValue().isBlank()) {
                children.add(node.getNodeValue());
            } else if (node.getNodeType() == Node.COMMENT_NODE || node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                children.add(node.getNodeType() + ":" + node.getNodeName() + ":" + node.getNodeValue());
            }
        }
        return new ElementSnapshot(element.getNamespaceURI(), element.getLocalName(), attributes, children);
    }

    private record ElementSnapshot(String namespace, String name, Map<String, String> attributes, List<Object> children) {}

    private static Set<String> fileNames(ZipFile zip) {
        Set<String> names = new HashSet<>();
        zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> names.add(entry.getName()));
        return names;
    }

    private static byte[] digest(ZipFile zip, ZipEntry entry) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (InputStream input = new DigestInputStream(zip.getInputStream(entry), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static ZipEntry boundedEntry(ZipFile zip, String name, long maximumSize) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > maximumSize) {
            throw new IOException("EPUB required entry is missing or too large");
        }
        return entry;
    }

    private static boolean safeName(String name) {
        return name != null && !name.isBlank() && !name.startsWith("/") && !name.contains("\\")
                && !name.contains(":") && !name.contains("//")
                && Arrays.stream(name.split("/")).noneMatch(part -> part.equals(".") || part.equals(".."));
    }
}
