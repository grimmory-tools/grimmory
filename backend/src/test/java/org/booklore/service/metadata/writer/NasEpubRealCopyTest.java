package org.booklore.service.metadata.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasEpubRealCopyTest {

    @TempDir
    Path tempDirectory;

    @Test
    void updatesOnlyWorkingCopiesAndPreservesOtherArchiveEntries() throws Exception {
        Path inputDirectory = Files.createDirectory(tempDirectory.resolve("input"));
        Path workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        List<String> originalHashes = List.of(
                createEpub(inputDirectory.resolve("one.epub"), 1),
                createEpub(inputDirectory.resolve("two.epub"), 2),
                createEpub(inputDirectory.resolve("three.epub"), 3));

        NasEpubRealCopyTestRunner.Summary summary =
                NasEpubRealCopyTestRunner.run(inputDirectory, workDirectory);

        assertThat(summary).isEqualTo(new NasEpubRealCopyTestRunner.Summary(3, 3, 3, 3, 0));
        assertThat(List.of("one.epub", "two.epub", "three.epub").stream()
                .map(name -> sha256(inputDirectory.resolve(name))))
                .containsExactlyElementsOf(originalHashes);
        try (var entries = Files.list(workDirectory)) {
            assertThat(entries.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".epub")).sorted())
                    .containsExactly("sample-001.epub", "sample-002.epub", "sample-003.epub");
        }
    }

    @Test
    void rejectsCorruptedZipEntryBeforeWritingMetadata() throws Exception {
        Path inputDirectory = Files.createDirectory(tempDirectory.resolve("input"));
        Path workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        Path corrupt = inputDirectory.resolve("one.epub");
        createEpub(corrupt, 1);
        createEpub(inputDirectory.resolve("two.epub"), 2);
        createEpub(inputDirectory.resolve("three.epub"), 3);

        byte[] bytes = Files.readAllBytes(corrupt);
        byte[] marker = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
        int offset = -1;
        for (int i = 0; i <= bytes.length - marker.length; i++) {
            if (java.util.Arrays.equals(bytes, i, i + marker.length, marker, 0, marker.length)) {
                offset = i;
                break;
            }
        }
        assertThat(offset).isGreaterThanOrEqualTo(0);
        bytes[offset] ^= 1;
        Files.write(corrupt, bytes);

        assertThatThrownBy(() -> NasEpubRealCopyTestRunner.run(inputDirectory, workDirectory))
                .isInstanceOf(IOException.class)
                .hasMessage("SAMPLE_001_ARCHIVE_ENTRY_INTEGRITY_FAILED");
        assertThat(Files.readAllBytes(corrupt)).isEqualTo(bytes);
    }

    @Test
    void reportsWhenNonTargetBookloreMetadataIsLost() throws Exception {
        Path inputDirectory = Files.createDirectory(tempDirectory.resolve("input"));
        Path workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        createEpub(inputDirectory.resolve("one.epub"), 1, true);
        createEpub(inputDirectory.resolve("two.epub"), 2);
        createEpub(inputDirectory.resolve("three.epub"), 3);

        assertThatThrownBy(() -> NasEpubRealCopyTestRunner.run(inputDirectory, workDirectory))
                .isInstanceOf(IOException.class)
                .hasMessage("SAMPLE_001_NON_TARGET_METADATA_CHANGED");
    }

    @Test
    void reportsRemovalOfOnlyPreviouslyInvalidRefinements() throws Exception {
        Path inputDirectory = Files.createDirectory(tempDirectory.resolve("input"));
        Path workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        createEpub(inputDirectory.resolve("one.epub"), 1, """
                <meta refines="#missing" property="role">aut</meta>
                <meta refines="#missing" property="role">aut</meta>
                <meta refines="#book-id" property="identifier-type">isbn</meta>
                """);
        createEpub(inputDirectory.resolve("two.epub"), 2);
        createEpub(inputDirectory.resolve("three.epub"), 3);

        assertThat(NasEpubRealCopyTestRunner.run(inputDirectory, workDirectory))
                .isEqualTo(new NasEpubRealCopyTestRunner.Summary(3, 3, 3, 3, 2));
    }

    @Test
    void rejectsRemovalOfRefinementsWhoseTargetIsLostDuringTitleUpdate() throws Exception {
        Path inputDirectory = Files.createDirectory(tempDirectory.resolve("input"));
        Path workDirectory = Files.createDirectory(tempDirectory.resolve("work"));
        createEpub(inputDirectory.resolve("one.epub"), 1,
                "<meta refines=\"#nested\" property=\"file-as\">Sort title</meta>",
                "Original <span id=\"nested\">title</span>");
        createEpub(inputDirectory.resolve("two.epub"), 2);
        createEpub(inputDirectory.resolve("three.epub"), 3);

        assertThatThrownBy(() -> NasEpubRealCopyTestRunner.run(inputDirectory, workDirectory))
                .isInstanceOf(IOException.class)
                .hasMessage("SAMPLE_001_NON_TARGET_METADATA_CHANGED");
    }

    private String createEpub(Path path, int index) throws IOException {
        return createEpub(path, index, false);
    }

    private String createEpub(Path path, int index, boolean includeBookloreMetadata) throws IOException {
        return createEpub(path, index,
                includeBookloreMetadata ? "<meta property=\"booklore:page_count\">123</meta>" : "");
    }

    private String createEpub(Path path, int index, String extraMetadata) throws IOException {
        return createEpub(path, index, extraMetadata, "Original title " + index);
    }

    private String createEpub(Path path, int index, String extraMetadata, String title) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeStoredEntry(zip, "mimetype", "application/epub+zip");
            writeEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            writeEntry(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:identifier id="book-id">urn:uuid:test-%d</dc:identifier>
                        <dc:title id="main">%s</dc:title>
                        <dc:creator>Original author</dc:creator>
                        <dc:subject>Original category</dc:subject>
                        <meta refines="#main" property="title-type">main</meta>
                        %s
                      </metadata>
                      <manifest/><spine/>
                    </package>
                    """.formatted(index, title, extraMetadata));
            writeEntry(zip, "OEBPS/chapter.xhtml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <body><p>Unchanged chapter %d</p></body>
                    </html>
                    """.formatted(index));
        }
        return sha256(path);
    }

    private void writeStoredEntry(ZipOutputStream zip, String name, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
