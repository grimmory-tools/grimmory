package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.ArchiveService;
import org.booklore.util.SecureXmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class NasEpubRealCopyTestRunner {

    private static final long MAX_INPUT_BYTES = 200L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final String DC_NAMESPACE = "http://purl.org/dc/elements/1.1/";
    private static final String OPF_NAMESPACE = "http://www.idpf.org/2007/opf";

    private NasEpubRealCopyTestRunner() {
    }

    public static void main(String[] args) {
        try {
            Path inputDirectory = requiredDirectory("NAS_REAL_EPUB_INPUT_DIR");
            Path workDirectory = requiredDirectory("NAS_REAL_EPUB_WORK_DIR");
            Summary summary = run(inputDirectory, workDirectory);
            System.out.printf("REAL_EPUB_COPY_TEST=PASSED%nEPUBS_FOUND=%d%nCOPIES_WRITTEN=%d%n"
                            + "INPUTS_UNCHANGED=%d%nZIP_VALIDATED=%d%nINVALID_REFINEMENTS_REMOVED=%d%n",
                    summary.inputsFound(), summary.copiesWritten(), summary.inputsUnchanged(),
                    summary.archivesValidated(), summary.invalidRefinementsRemoved());
        } catch (Exception exception) {
            System.err.println("REAL_EPUB_COPY_TEST=FAILED");
            if (exception instanceof TestFailureException failure) {
                System.err.println("FAILURE_CODE=" + failure.getMessage());
            } else {
                System.err.println("FAILURE_TYPE=" + exception.getClass().getSimpleName());
            }
            System.exit(1);
        }
    }

    static Summary run(Path inputDirectory, Path workDirectory) throws IOException {
        Path inputRoot = validateDirectory(inputDirectory);
        Path workRoot = validateDirectory(workDirectory);
        if (inputRoot.equals(workRoot) || inputRoot.startsWith(workRoot) || workRoot.startsWith(inputRoot)) {
            throw new TestFailureException("INPUT_AND_WORK_DIRECTORIES_OVERLAP");
        }

        List<Path> inputFiles;
        try (var entries = Files.list(inputRoot)) {
            List<Path> allEntries = entries.toList();
            for (Path entry : allEntries) {
                if (isEpub(entry) && (Files.isSymbolicLink(entry)
                        || !Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))) {
                    throw new TestFailureException("INPUT_CONTAINS_NON_REGULAR_EPUB");
                }
            }
            inputFiles = allEntries.stream()
                    .filter(NasEpubRealCopyTestRunner::isEpub)
                    .sorted((left, right) -> left.getFileName().toString()
                            .compareToIgnoreCase(right.getFileName().toString()))
                    .toList();
        }

        if (inputFiles.size() != 3) {
            throw new TestFailureException("EXACTLY_THREE_INPUT_EPUB_COPIES_REQUIRED");
        }
        Path temporaryDirectory = workRoot.resolve("tmp");
        try (var entries = Files.list(workRoot)) {
            List<Path> existingEntries = entries.toList();
            if (existingEntries.stream().anyMatch(entry -> !entry.equals(temporaryDirectory))) {
                throw new TestFailureException("WORK_DIRECTORY_MUST_BE_EMPTY");
            }
        }
        if (Files.isSymbolicLink(temporaryDirectory)
                || (Files.exists(temporaryDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(temporaryDirectory, LinkOption.NOFOLLOW_LINKS))) {
            throw new TestFailureException("TEMP_DIRECTORY_INVALID");
        }
        Files.createDirectories(temporaryDirectory);
        try (var entries = Files.list(temporaryDirectory)) {
            if (entries.findAny().isPresent()) {
                throw new TestFailureException("TEMP_DIRECTORY_MUST_BE_EMPTY");
            }
        }

        MetadataPersistenceSettings settings = createSettings();
        EpubMetadataWriter writer = new EpubMetadataWriter(() -> settings, new ArchiveService());
        int copiesWritten = 0;
        int inputsUnchanged = 0;
        int archivesValidated = 0;
        int invalidRefinementsRemoved = 0;

        for (int index = 0; index < inputFiles.size(); index++) {
            Path input = inputFiles.get(index);
            Path output = workRoot.resolve("sample-%03d.epub".formatted(index + 1));
            String originalHash = sha256(input);
            try {
                long inputSize = Files.size(input);
                if (inputSize <= 0 || inputSize > MAX_INPUT_BYTES) {
                    throw new TestFailureException("INPUT_SIZE_OUT_OF_RANGE");
                }
                Files.copy(input, output);
                if (!originalHash.equals(sha256(output))) {
                    throw new TestFailureException("COPIED_INPUT_HASH_MISMATCH");
                }

                ArchiveSnapshot before = inspectArchive(output);
                checkAvailableSpace(workRoot, before.uncompressedBytes());

                BookMetadataEntity metadata = new BookMetadataEntity();
                metadata.setTitle("Grimmory isolated NAS test sample %03d".formatted(index + 1));
                metadata.setAuthors(null);
                metadata.setCategories(null);
                metadata.setMoods(null);
                metadata.setTags(null);
                writer.saveMetadataToFileStrict(output.toFile(), metadata, new MetadataClearFlags());

                ArchiveSnapshot after = inspectArchive(output);
                if (!before.opfPath().equals(after.opfPath())
                        || !before.entryHashes().keySet().equals(after.entryHashes().keySet())) {
                    throw new TestFailureException("ARCHIVE_STRUCTURE_CHANGED");
                }
                for (Map.Entry<String, String> entry : before.entryHashes().entrySet()) {
                    if (!entry.getKey().equals(before.opfPath())
                            && !entry.getValue().equals(after.entryHashes().get(entry.getKey()))) {
                        throw new TestFailureException("NON_METADATA_ENTRY_CHANGED");
                    }
                }
                if (!metadata.getTitle().equals(after.mainTitle())) {
                    throw new TestFailureException("UPDATED_TITLE_NOT_FOUND");
                }
                int removedRefinements = countRemovedInvalidRefinements(before, after);
                if (!"application/epub+zip".equals(after.mimetype())) {
                    throw new TestFailureException("INVALID_EPUB_MIMETYPE");
                }
                if (!originalHash.equals(sha256(input))) {
                    throw new TestFailureException("INPUT_COPY_WAS_MODIFIED");
                }
                if (Files.exists(output.resolveSibling(output.getFileName() + ".bak"))
                        || Files.exists(output.resolveSibling(output.getFileName() + ".tmp"))) {
                    throw new TestFailureException("TEMPORARY_ARTIFACT_REMAINS");
                }

                copiesWritten++;
                inputsUnchanged++;
                archivesValidated++;
                invalidRefinementsRemoved += removedRefinements;
                System.out.printf("SAMPLE_%03d_INVALID_REFINEMENTS_REMOVED=%d%n", index + 1, removedRefinements);
            } catch (Exception exception) {
                if (exception instanceof TestFailureException failure) {
                    throw new TestFailureException("SAMPLE_%03d_%s".formatted(index + 1, failure.getMessage()),
                            failure);
                }
                throw new TestFailureException("SAMPLE_%03d_%s".formatted(index + 1,
                        exception.getClass().getSimpleName()), exception);
            }
        }

        try (var entries = Files.list(workRoot)) {
            List<Path> outputs = entries.toList();
            if (outputs.size() != 4 || !outputs.contains(temporaryDirectory)
                    || outputs.stream().filter(entry -> !entry.equals(temporaryDirectory))
                    .anyMatch(entry -> !entry.getFileName().toString().matches("sample-\\d{3}\\.epub"))) {
                throw new TestFailureException("UNEXPECTED_WORK_DIRECTORY_CONTENTS");
            }
        }
        try (var entries = Files.list(temporaryDirectory)) {
            if (entries.findAny().isPresent()) {
                throw new TestFailureException("TEMPORARY_ARTIFACT_REMAINS");
            }
        }

        return new Summary(inputFiles.size(), copiesWritten, inputsUnchanged, archivesValidated,
                invalidRefinementsRemoved);
    }

    private static int countRemovedInvalidRefinements(ArchiveSnapshot before, ArchiveSnapshot after)
            throws TestFailureException {
        int removed = 0;
        for (var entry : after.metadata().entrySet()) {
            if (entry.getValue() > before.metadata().getOrDefault(entry.getKey(), 0)) {
                throw new TestFailureException("NON_TARGET_METADATA_CHANGED");
            }
        }
        for (var entry : before.metadata().entrySet()) {
            int difference = entry.getValue() - after.metadata().getOrDefault(entry.getKey(), 0);
            if (difference > before.invalidRefinements().getOrDefault(entry.getKey(), 0)) {
                throw new TestFailureException("NON_TARGET_METADATA_CHANGED");
            }
            removed += difference;
        }
        return removed;
    }

    private static Path requiredDirectory(String environmentVariable) throws IOException {
        String value = System.getenv(environmentVariable);
        if (value == null || value.isBlank()) {
            throw new TestFailureException("MISSING_" + environmentVariable);
        }
        return validateDirectory(Path.of(value));
    }

    private static Path validateDirectory(Path directory) throws IOException {
        if (directory == null || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new TestFailureException("TEST_DIRECTORY_INVALID");
        }
        return directory.toRealPath();
    }

    private static boolean isEpub(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".epub");
    }

    private static MetadataPersistenceSettings createSettings() {
        MetadataPersistenceSettings.FormatSettings epubSettings =
                MetadataPersistenceSettings.FormatSettings.builder()
                        .enabled(true)
                        .maxFileSizeInMb((int) (MAX_INPUT_BYTES / (1024 * 1024)))
                        .build();
        MetadataPersistenceSettings.SaveToOriginalFile saveSettings =
                MetadataPersistenceSettings.SaveToOriginalFile.builder()
                        .epub(epubSettings)
                        .build();
        MetadataPersistenceSettings settings = new MetadataPersistenceSettings();
        settings.setSaveToOriginalFile(saveSettings);
        return settings;
    }

    private static void checkAvailableSpace(Path workDirectory, long uncompressedBytes) throws IOException {
        long required = Math.addExact(Math.multiplyExact(uncompressedBytes, 2), 128L * 1024 * 1024);
        if (Files.getFileStore(workDirectory).getUsableSpace() < required) {
            throw new TestFailureException("INSUFFICIENT_WORK_SPACE");
        }
    }

    private static ArchiveSnapshot inspectArchive(Path archivePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            Map<String, String> entryHashes = new TreeMap<>();
            long totalBytes = 0;
            int entryCount = 0;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > MAX_ARCHIVE_ENTRIES || entry.getSize() < 0
                        || entry.getSize() > MAX_UNCOMPRESSED_BYTES - totalBytes) {
                    throw new TestFailureException("ARCHIVE_LIMIT_EXCEEDED");
                }
                totalBytes += entry.getSize();
                String hash;
                try (InputStream input = zipFile.getInputStream(entry)) {
                    hash = hashArchiveEntry(input, entry);
                }
                if (entryHashes.putIfAbsent(entry.getName(), hash) != null) {
                    throw new TestFailureException("DUPLICATE_ARCHIVE_ENTRY");
                }
            }

            ZipEntry mimetypeEntry = zipFile.getEntry("mimetype");
            if (mimetypeEntry == null || mimetypeEntry.getSize() != "application/epub+zip".length()) {
                throw new TestFailureException("INVALID_EPUB_MIMETYPE");
            }
            String mimetype;
            try (InputStream input = zipFile.getInputStream(mimetypeEntry)) {
                mimetype = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII);
            }

            ZipEntry containerEntry = zipFile.getEntry("META-INF/container.xml");
            if (containerEntry == null || containerEntry.getSize() > 1024 * 1024) {
                throw new TestFailureException("EPUB_CONTAINER_MISSING_OR_TOO_LARGE");
            }
            String opfPath = readOpfPath(zipFile, containerEntry);
            ZipEntry opfEntry = zipFile.getEntry(opfPath);
            if (opfEntry == null || opfEntry.getSize() > 8 * 1024 * 1024) {
                throw new TestFailureException("EPUB_PACKAGE_MISSING_OR_TOO_LARGE");
            }
            PackageMetadata metadata = readMetadata(zipFile, opfEntry);
            return new ArchiveSnapshot(opfPath, entryHashes, metadata.mainTitle(), metadata.elements(),
                    metadata.invalidRefinements(), mimetype, totalBytes);
        } catch (ParserConfigurationException | org.xml.sax.SAXException exception) {
            throw new TestFailureException("EPUB_XML_INVALID", exception);
        } catch (ArithmeticException exception) {
            throw new TestFailureException("ARCHIVE_SIZE_OVERFLOW", exception);
        }
    }

    private static String readOpfPath(ZipFile zipFile, ZipEntry containerEntry)
            throws IOException, ParserConfigurationException, org.xml.sax.SAXException {
        Document document;
        try (InputStream input = zipFile.getInputStream(containerEntry)) {
            document = SecureXmlUtils.createSecureDocumentBuilder(true).parse(input);
        }
        NodeList rootfiles = document.getElementsByTagNameNS("*", "rootfile");
        if (rootfiles.getLength() == 0) {
            throw new TestFailureException("EPUB_ROOTFILE_MISSING");
        }
        String path = ((Element) rootfiles.item(0)).getAttribute("full-path");
        if (path.isBlank() || path.startsWith("/") || List.of(path.split("/")).contains("..")) {
            throw new TestFailureException("EPUB_ROOTFILE_PATH_INVALID");
        }
        return path;
    }

    private static PackageMetadata readMetadata(ZipFile zipFile, ZipEntry opfEntry)
            throws IOException, ParserConfigurationException, org.xml.sax.SAXException {
        Document document;
        try (InputStream input = zipFile.getInputStream(opfEntry)) {
            document = SecureXmlUtils.createSecureDocumentBuilder(true).parse(input);
        }
        NodeList metadataElements = document.getElementsByTagNameNS("*", "metadata");
        if (metadataElements.getLength() != 1) {
            throw new TestFailureException("EPUB_METADATA_INVALID");
        }
        Element metadata = (Element) metadataElements.item(0);
        List<Element> titles = new ArrayList<>();
        List<Element> children = new ArrayList<>();
        for (Node node = metadata.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                children.add(element);
                if (DC_NAMESPACE.equals(element.getNamespaceURI()) && "title".equals(element.getLocalName())) {
                    titles.add(element);
                }
            }
        }
        if (titles.isEmpty()) {
            throw new TestFailureException("EPUB_TITLE_MISSING");
        }
        Element mainTitle = titles.getFirst();
        findMainTitle:
        for (Element element : children) {
            if (OPF_NAMESPACE.equals(element.getNamespaceURI()) && "meta".equals(element.getLocalName())
                    && "title-type".equals(element.getAttribute("property"))
                    && "main".equalsIgnoreCase(element.getTextContent().trim())) {
                String refines = element.getAttribute("refines");
                for (Element title : titles) {
                    if (!title.getAttribute("id").isEmpty()
                            && refines.equals("#" + title.getAttribute("id"))) {
                        mainTitle = title;
                        break findMainTitle;
                    }
                }
            }
        }
        String title = mainTitle.getTextContent();
        Set<String> ids = new HashSet<>();
        NodeList allElements = document.getElementsByTagName("*");
        for (int index = 0; index < allElements.getLength(); index++) {
            String id = ((Element) allElements.item(index)).getAttribute("id");
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        mainTitle.setTextContent("GRIMMORY_TEST_TITLE_PLACEHOLDER");
        Map<ElementSnapshot, Integer> elements = new HashMap<>();
        Map<ElementSnapshot, Integer> invalidRefinements = new HashMap<>();
        for (Element element : children) {
            ElementSnapshot elementSnapshot = snapshot(element);
            elements.merge(elementSnapshot, 1, Integer::sum);
            String refines = element.getAttribute("refines");
            if ("meta".equals(element.getLocalName()) && refines.startsWith("#")
                    && !ids.contains(refines.substring(1))) {
                invalidRefinements.merge(elementSnapshot, 1, Integer::sum);
            }
        }
        return new PackageMetadata(title, elements, invalidRefinements);
    }

    private static ElementSnapshot snapshot(Element element) {
        Map<String, String> attributes = new TreeMap<>();
        var nodes = element.getAttributes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node attribute = nodes.item(index);
            if (!"http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) {
                attributes.put("{" + attribute.getNamespaceURI() + "}" + attribute.getLocalName(),
                        attribute.getNodeValue());
            }
        }
        List<Object> children = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                children.add(snapshot(child));
            } else if ((node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !node.getNodeValue().isBlank()) {
                children.add(node.getNodeValue());
            }
        }
        return new ElementSnapshot(element.getNamespaceURI(), element.getLocalName(), attributes, children);
    }

    private static String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input, Long.MAX_VALUE);
        }
    }

    private static String hashArchiveEntry(InputStream input, ZipEntry entry) throws IOException {
        MessageDigest digest = newSha256();
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[8192];
        long readBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            readBytes += read;
            if (readBytes > entry.getSize()) {
                throw new TestFailureException("ARCHIVE_EXPANSION_LIMIT_EXCEEDED");
            }
            digest.update(buffer, 0, read);
            crc.update(buffer, 0, read);
        }
        if (readBytes != entry.getSize() || crc.getValue() != entry.getCrc()) {
            throw new TestFailureException("ARCHIVE_ENTRY_INTEGRITY_FAILED");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(InputStream input, long maximumBytes) throws IOException {
        MessageDigest digest = newSha256();
        byte[] buffer = new byte[8192];
        long readBytes = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            readBytes += read;
            if (readBytes > maximumBytes) {
                throw new TestFailureException("ARCHIVE_EXPANSION_LIMIT_EXCEEDED");
            }
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Summary(int inputsFound, int copiesWritten, int inputsUnchanged, int archivesValidated,
                   int invalidRefinementsRemoved) {
    }

    private record ArchiveSnapshot(String opfPath, Map<String, String> entryHashes, String mainTitle,
                                   Map<ElementSnapshot, Integer> metadata,
                                   Map<ElementSnapshot, Integer> invalidRefinements, String mimetype,
                                   long uncompressedBytes) {
    }

    private record PackageMetadata(String mainTitle, Map<ElementSnapshot, Integer> elements,
                                   Map<ElementSnapshot, Integer> invalidRefinements) {
    }

    private record ElementSnapshot(String namespace, String name, Map<String, String> attributes,
                                   List<Object> children) {
    }

    private static final class TestFailureException extends IOException {
        private TestFailureException(String message) {
            super(message);
        }

        private TestFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
