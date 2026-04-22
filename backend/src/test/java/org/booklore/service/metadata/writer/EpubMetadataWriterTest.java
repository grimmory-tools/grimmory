package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpubMetadataWriterTest {

    private EpubMetadataWriter writer;
    private BookMetadataEntity metadata;
    private BookEntity bookEntity;
    private AppSettingService appSettingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        appSettingService = mock(AppSettingService.class);
        MetadataPersistenceSettings.FormatSettings epubFormatSettings = MetadataPersistenceSettings.FormatSettings.builder()
                .enabled(true)
                .maxFileSizeInMb(100)
                .build();
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile = MetadataPersistenceSettings.SaveToOriginalFile.builder()
                .epub(epubFormatSettings)
                .build();
        MetadataPersistenceSettings metadataPersistenceSettings = new MetadataPersistenceSettings();
        metadataPersistenceSettings.setSaveToOriginalFile(saveToOriginalFile);

        AppSettings appSettings = mock(AppSettings.class);
        when(appSettings.getMetadataPersistenceSettings()).thenReturn(metadataPersistenceSettings);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        writer = new EpubMetadataWriter(appSettingService);
        metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        AuthorEntity author = new AuthorEntity();
        author.setName("Test Author");
        metadata.setAuthors(List.of(author));

        bookEntity = new BookEntity();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        bookEntity.setLibraryPath(libraryPath);
        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(bookEntity);
        bookEntity.setBookFiles(Collections.singletonList(primaryFile));
        bookEntity.getPrimaryBookFile().setFileSubPath("");
        bookEntity.getPrimaryBookFile().setFileName("test.epub");
    }

    @Nested
    @DisplayName("Metadata writing Tests")
    class MetadataWritingTests {
        @Test
        @DisplayName("Should only overwrite authors of EPUB metadata")
        void writeMetadata_withAuthor_onlyAuthor() throws IOException {
            StringBuilder existingMetadata = new StringBuilder();
            existingMetadata.append("<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">");
            existingMetadata.append("<dc:creator id=\"creator02\">Alice</dc:creator>");
            existingMetadata.append("<meta property=\"role\" refines=\"#creator02\">ill</meta>");
            existingMetadata.append("</metadata>");
            String opfContent = String.format("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        %s
                    </package>
                    """, existingMetadata);
            File epubFile = createEpubWithOpf(opfContent, "test-metadata-" + System.nanoTime() + ".epub");

            assertDoesNotThrow(() -> writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
            try (ZipFile zf = new ZipFile(epubFile)) {
                ZipEntry ze = zf.getEntry("OEBPS/content.opf");
                try (InputStream is = zf.getInputStream(ze)) {
                    byte[] fileBytes = is.readAllBytes();
                    String fileString = new String(fileBytes);
                    assertTrue(fileString.contains("id=\"creator02\""));
                }
            }
        }
    }

    @Nested
    @DisplayName("URL Decoding Tests")
    class UrlDecodingTests {

        @Test
        @DisplayName("Should properly handle URL-encoded href values in manifest")
        void writeMetadataToFile_withUnicodeHref_handlesDecoding() throws IOException {
            byte[] epubContent = createEpubWithUnicodeCoverHref();
            File epubFile = tempDir.resolve("test_unicode.epub").toFile();
            Files.write(epubFile.toPath(), epubContent);

            assertDoesNotThrow(() -> writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags()));

            assertTrue(epubFile.exists());
            assertTrue(epubFile.length() > 0);
        }

        @Test
        @DisplayName("Should handle URL-encoded cover href during cover replacement")
        void replaceCoverImageFromUpload_withUnicodeHref_handlesDecoding() throws IOException {
            byte[] epubContent = createEpubWithUnicodeCoverHref();
            File epubFile = tempDir.resolve("test_cover_unicode.epub").toFile();
            Files.write(epubFile.toPath(), epubContent);

            byte[] imageBytes = createMinimalPngImage();
            MultipartFile coverFile = new MockMultipartFile(
                    "cover.png",
                    "cover.png",
                    "image/png",
                    imageBytes
            );

            assertDoesNotThrow(() -> writer.replaceCoverImageFromUpload(bookEntity, coverFile));
        }
    }

    @Nested
    @DisplayName("Whitespace Tests")
    class WhitespaceTests {
        @Test
        @DisplayName("Should not add extra whitespace lines on repeated saves")
        void saveMetadataToFile_repeatedSaves_shouldNotInflateWhitespace() throws IOException {
            String initialOpfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                            <dc:creator>Original Author</dc:creator>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(initialOpfContent, "test-whitespace-" + System.nanoTime() + ".epub");

            BookMetadataEntity newMeta = new BookMetadataEntity();
            newMeta.setTitle("Updated Title");
            AuthorEntity author = new AuthorEntity();
            author.setName("Updated Author");
            newMeta.setAuthors(List.of(author));

            writer.saveMetadataToFile(epubFile, newMeta, null, new MetadataClearFlags());
            String contentAfterFirstSave = readOpfContent(epubFile);

            newMeta.setTitle("Updated Title 2"); // Change title to force write
            writer.saveMetadataToFile(epubFile, newMeta, null, new MetadataClearFlags());
            String contentAfterSecondSave = readOpfContent(epubFile);

            long lines1 = contentAfterFirstSave.lines().count();
            long lines2 = contentAfterSecondSave.lines().count();

            assertTrue(Math.abs(lines2 - lines1) <= 2, "Line count should be stable");
            assertTrue(!contentAfterSecondSave.contains("\n\n"), "Should not contain double newlines");
        }
    }

    @Nested
    @DisplayName("EPUB3 Creator Metadata Tests")
    class Epub3CreatorTests {

        @Test
        @DisplayName("Should use meta refines for file-as in EPUB3")
        void epub3_shouldUseMetaRefines_forFileAs() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-creator-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            Document doc = parseOpf(epubFile);
            NodeList creators = doc.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "creator");
            assertThat(creators.getLength()).isGreaterThan(0);

            Element creator = (Element) creators.item(0);
            assertThat(creator.getTextContent()).isEqualTo("Test Author");
            assertThat(creator.getAttribute("id")).isNotEmpty();

            // Should NOT have opf:file-as or opf:role attributes
            String fileAsAttr = creator.getAttributeNS("http://www.idpf.org/2007/opf", "file-as");
            String roleAttr = creator.getAttributeNS("http://www.idpf.org/2007/opf", "role");
            assertThat(fileAsAttr).isEmpty();
            assertThat(roleAttr).isEmpty();

            // Should have meta refines elements instead
            String creatorId = creator.getAttribute("id");
            String opfContent2 = readOpfContent(epubFile);
            assertThat(opfContent2).contains("refines=\"#" + creatorId + "\"");
            assertThat(opfContent2).contains("property=\"file-as\"");
            assertThat(opfContent2).contains("property=\"role\"");
        }

        @Test
        @DisplayName("Should include role with marc:relators scheme in EPUB3")
        void epub3_shouldIncludeRoleScheme() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-role-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("scheme=\"marc:relators\"");
            assertThat(content).contains(">aut<");
        }
    }

    @Nested
    @DisplayName("EPUB2 Creator Metadata Tests")
    class Epub2CreatorTests {

        @Test
        @DisplayName("Should use opf:file-as attribute on dc:creator in EPUB2")
        void epub2_shouldUseOpfAttributes() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-creator-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("opf:file-as=");
            assertThat(content).contains("opf:role=");
            // Should NOT have meta refines for creators
            assertThat(content).doesNotContain("property=\"file-as\"");
            assertThat(content).doesNotContain("property=\"role\"");
        }

        @Test
        @DisplayName("Should preserve EPUB2 structure without EPUB3 constructs")
        void epub2_shouldNotContainEpub3Constructs() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>EPUB2 Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSubtitle("A Subtitle");
            metadata.setSeriesName("Test Series");
            metadata.setSeriesNumber(3.0f);
            metadata.setPageCount(200);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-no-epub3-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            // EPUB2 should not have prefix attribute on package
            assertThat(content).doesNotContain("prefix=");
            // EPUB2 should not have property= metas for series
            assertThat(content).doesNotContain("property=\"belongs-to-collection\"");
            assertThat(content).doesNotContain("property=\"collection-type\"");
            assertThat(content).doesNotContain("property=\"group-position\"");
            // EPUB2 should not have property= metas for subtitles
            assertThat(content).doesNotContain("property=\"title-type\"");
        }
    }

    @Nested
    @DisplayName("Mimetype ZIP Entry Tests")
    class MimetypeZipTests {

        @Test
        @DisplayName("Should store mimetype as first uncompressed entry in ZIP")
        void saveMetadata_shouldStoreMimetypeFirst() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-mimetype-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            try (ZipFile zf = new ZipFile(epubFile)) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                assertThat(entries.hasMoreElements()).isTrue();

                ZipEntry firstEntry = entries.nextElement();
                assertThat(firstEntry.getName()).isEqualTo("mimetype");
                assertThat(firstEntry.getMethod()).isEqualTo(ZipEntry.STORED);

                // Verify mimetype content
                try (InputStream is = zf.getInputStream(firstEntry)) {
                    String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    assertThat(content).isEqualTo("application/epub+zip");
                }
            }
        }

        @Test
        @DisplayName("Should not duplicate mimetype entry in ZIP")
        void saveMetadata_shouldNotDuplicateMimetype() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Original Title</dc:title>
                        </metadata>
                    </package>""";

            File epubFile = createEpubWithOpf(opfContent, "test-no-dup-mimetype-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            try (ZipFile zf = new ZipFile(epubFile)) {
                int mimetypeCount = 0;
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    if ("mimetype".equals(entries.nextElement().getName())) {
                        mimetypeCount++;
                    }
                }
                assertThat(mimetypeCount).isEqualTo(1);
            }
        }
    }

    @Nested
    @DisplayName("EPUB3 Series Metadata Tests")
    class Epub3SeriesTests {

        @Test
        @DisplayName("Should use belongs-to-collection for series in EPUB3")
        void epub3_shouldUseBelongsToCollection() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Series Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSeriesName("The Dark Tower");
            metadata.setSeriesNumber(3.0f);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-series-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("property=\"belongs-to-collection\"");
            assertThat(content).contains("The Dark Tower");
            assertThat(content).contains("property=\"collection-type\"");
            assertThat(content).contains("series");
            assertThat(content).contains("property=\"group-position\"");
            assertThat(content).contains(">3<");
        }
    }

    @Nested
    @DisplayName("EPUB2 Series Metadata Tests")
    class Epub2SeriesTests {

        @Test
        @DisplayName("Should use calibre:series convention for series in EPUB2")
        void epub2_shouldUseCalibreSeries() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Series Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setSeriesName("Wheel of Time");
            metadata.setSeriesNumber(5.0f);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-series-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("name=\"calibre:series\"");
            assertThat(content).contains("content=\"Wheel of Time\"");
            assertThat(content).contains("name=\"calibre:series_index\"");
            assertThat(content).contains("content=\"5\"");
            // Should NOT contain EPUB3 series constructs
            assertThat(content).doesNotContain("belongs-to-collection");
            assertThat(content).doesNotContain("collection-type");
            assertThat(content).doesNotContain("group-position");
        }
    }

    @Nested
    @DisplayName("EPUB3 Subtitle Tests")
    class Epub3SubtitleTests {

        @Test
        @DisplayName("Should add subtitle as separate dc:title with title-type refinement in EPUB3")
        void epub3_shouldAddSubtitleWithRefinement() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Main Title</dc:title>
                        </metadata>
                    </package>""";

            metadata.setTitle("Main Title");
            metadata.setSubtitle("A Great Subtitle");

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("A Great Subtitle");
            assertThat(content).contains("property=\"title-type\"");
            assertThat(content).contains(">subtitle<");
        }
    }

    @Nested
    @DisplayName("EPUB2 Subtitle Tests")
    class Epub2SubtitleTests {

        @Test
        @DisplayName("Should store subtitle via booklore:subtitle metadata in EPUB2 without modifying dc:title")
        void epub2_shouldStoreSubtitleInBookloreMeta() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Main Title</dc:title>
                        </metadata>
                    </package>""";

            metadata.setTitle("Main Title");
            metadata.setSubtitle("A Great Subtitle");

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-subtitle-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            // Title should remain unchanged (not appended with subtitle)
            assertThat(content).contains(">Main Title<");
            assertThat(content).doesNotContain("Main Title: A Great Subtitle");
            // Subtitle should be stored via booklore:subtitle metadata
            assertThat(content).contains("name=\"booklore:subtitle\"");
            assertThat(content).contains("content=\"A Great Subtitle\"");
            // Should NOT have EPUB3 title-type refinement
            assertThat(content).doesNotContain("property=\"title-type\"");
        }
    }

    @Nested
    @DisplayName("EPUB3 Booklore Metadata Tests")
    class Epub3BookloreMetadataTests {

        @Test
        @DisplayName("Should use property attribute for booklore metadata in EPUB3")
        void epub3_shouldUsePropertyAttribute() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setPageCount(350);

            File epubFile = createEpubWithOpf(opfContent, "test-epub3-booklore-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("property=\"booklore:page_count\"");
            assertThat(content).contains(">350<");
            assertThat(content).contains("prefix=");
            assertThat(content).contains("booklore:");
        }
    }

    @Nested
    @DisplayName("EPUB2 Booklore Metadata Tests")
    class Epub2BookloreMetadataTests {

        @Test
        @DisplayName("Should use name/content attributes for booklore metadata in EPUB2")
        void epub2_shouldUseNameContentAttributes() throws Exception {
            String opfContent = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                            <dc:title>Test Book</dc:title>
                        </metadata>
                    </package>""";

            metadata.setPageCount(350);

            File epubFile = createEpubWithOpf(opfContent, "test-epub2-booklore-" + System.nanoTime() + ".epub");
            writer.saveMetadataToFile(epubFile, metadata, null, new MetadataClearFlags());

            String content = readOpfContent(epubFile);
            assertThat(content).contains("name=\"booklore:page_count\"");
            assertThat(content).contains("content=\"350\"");
            // Should NOT have EPUB3 prefix attribute
            assertThat(content).doesNotContain("prefix=");
            // Should NOT use property= form
            assertThat(content).doesNotContain("property=\"booklore:");
        }
    }

    private Document parseOpf(File epubFile) throws Exception {
        try (ZipFile zf = new ZipFile(epubFile)) {
            ZipEntry ze = zf.getEntry("OEBPS/content.opf");
            try (InputStream is = zf.getInputStream(ze)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(is);
            }
        }
    }

    private String readOpfContent(File epubFile) throws IOException {
        try (ZipFile zf = new ZipFile(epubFile)) {
            ZipEntry ze = zf.getEntry("OEBPS/content.opf");
            try (InputStream is = zf.getInputStream(ze)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private File createEpubWithOpf(String opfContent, String filename) throws IOException {
        File epubFile = tempDir.resolve(filename).toFile();

        String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epubFile))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return epubFile;
    }

    private byte[] createEpubWithUnicodeCoverHref() throws IOException {
        String opfContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Test Book with Unicode Cover</dc:title>
                        <dc:creator>Test Author</dc:creator>
                        <meta name="cover" content="cover-image"/>
                    </metadata>
                    <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="cover-image" href="cover%C3%A1.png" media-type="image/png" properties="cover-image"/>
                        <item id="text" href="index.html" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine toc="ncx">
                        <itemref idref="text"/>
                    </spine>
                </package>
                """;

        String containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """;

        String htmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>Test</title>
                </head>
                <body>
                    <h1>Test Content</h1>
                </body>
                </html>
                """;

        String ncxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
                    "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
                <ncx version="2005-1" xml:lang="en">
                    <head>
                        <meta name="dtb:uid" content="test-book"/>
                    </head>
                    <docTitle>
                        <text>Test Book</text>
                    </docTitle>
                    <navMap>
                        <navPoint id="navpoint-1" playOrder="1">
                            <navLabel>
                                <text>Test</text>
                            </navLabel>
                            <content src="index.html"/>
                        </navPoint>
                    </navMap>
                </ncx>
                """;

        byte[] coverImage = createMinimalPngImage();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/index.html"));
            zos.write(htmlContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("OEBPS/toc.ncx"));
            zos.write(ncxContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String decodedCoverPath = java.net.URLDecoder.decode("cover%C3%A1.png", java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry("OEBPS/" + decodedCoverPath));
            zos.write(coverImage);
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private byte[] createMinimalPngImage() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01,
                0x00, 0x00, 0x00, 0x01,
                0x08, 0x06,
                0x00, 0x00, 0x00,
                (byte) 0x90, (byte) 0x77, (byte) 0x53, (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0A,
                0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
                0x00, 0x01,
                0x0D, (byte) 0x0A, 0x2D, (byte) 0xB4,
                0x00, 0x00, 0x00, 0x00,
                0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}

