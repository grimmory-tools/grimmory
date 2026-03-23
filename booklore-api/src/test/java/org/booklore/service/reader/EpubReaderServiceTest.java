package org.booklore.service.reader;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.model.dto.response.EpubManifestItem;
import org.booklore.model.dto.response.EpubSpineItem;
import org.booklore.model.dto.response.EpubTocItem;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EpubReaderServiceTest {

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    EpubReaderService epubReaderService;

    BookEntity bookEntity;
    Path epubPath;

    @TempDir
    Path tempDir;

    private static final String CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
            """;

    private static final String CONTENT_OPF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Book</dc:title>
                    <dc:creator>Test Author</dc:creator>
                    <dc:language>en</dc:language>
                </metadata>
                <manifest>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chapter2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="style" href="style.css" media-type="text/css"/>
                    <item id="cover" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                </manifest>
                <spine>
                    <itemref idref="chapter1"/>
                    <itemref idref="chapter2"/>
                </spine>
            </package>
            """;

    private static final String NAV_XHTML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>Navigation</title></head>
            <body>
                <nav epub:type="toc">
                    <ol>
                        <li><a href="chapter1.xhtml">Chapter 1</a></li>
                        <li><a href="chapter2.xhtml">Chapter 2</a></li>
                    </ol>
                </nav>
            </body>
            </html>
            """;

    @BeforeEach
    void setup() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        epubPath = tempDir.resolve("test.epub");
        Files.deleteIfExists(epubPath);
    }

    @Test
    void testGetBookInfo_Success() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

                assertNotNull(bookInfo);
                assertEquals("OEBPS/content.opf", bookInfo.getContainerPath());
                assertEquals("OEBPS/", bookInfo.getRootPath());
                assertEquals("Test Book", bookInfo.getMetadata().get("title"));
                assertEquals("Test Author", bookInfo.getMetadata().get("creator"));
                assertEquals("en", bookInfo.getMetadata().get("language"));
                assertEquals(5, bookInfo.getManifest().size());
                assertEquals(2, bookInfo.getSpine().size());
                assertEquals("OEBPS/cover.jpg", bookInfo.getCoverPath());
            }
        }
    }

    @Test
    void testGetBookInfo_BookNotFound() {
        when(bookRepository.findByIdWithBookFiles(999L)).thenReturn(Optional.empty());

        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(),
                () -> epubReaderService.getBookInfo(999L));
    }

    @Test
    void testGetBookInfo_CacheHit() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First call - should parse
                EpubBookInfo bookInfo1 = epubReaderService.getBookInfo(1L);
                // Second call - should use cache
                EpubBookInfo bookInfo2 = epubReaderService.getBookInfo(1L);

                assertSame(bookInfo1, bookInfo2);
                // ZipFile builder should be called only once for parsing
                verify(builder, times(1)).get();
            }
        }
    }

    @Test
    void testStreamFile_Success() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            byte[] chapterContent = "<html><body>Chapter 1 content</body></html>".getBytes(StandardCharsets.UTF_8);
            ZipFile zipFile = createMockZipFileWithStreamableEntry("OEBPS/chapter1.xhtml", chapterContent);
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                // Reset mock for streaming call
                reset(builder);
                when(builder.setPath(epubPath)).thenReturn(builder);
                when(builder.setCharset(any(Charset.class))).thenReturn(builder);
                when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
                when(builder.get()).thenReturn(zipFile);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                epubReaderService.streamFile(1L, "chapter1.xhtml", outputStream);

                assertArrayEquals(chapterContent, outputStream.toByteArray());
            }
        }
    }

    @Test
    void testStreamFile_FileNotFound() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                assertThrows(FileNotFoundException.class,
                        () -> epubReaderService.streamFile(1L, "nonexistent.xhtml", outputStream));
            }
        }
    }

    @Test
    void testStreamFile_PathTraversalBlocked() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                assertThrows(FileNotFoundException.class,
                        () -> epubReaderService.streamFile(1L, "../../../etc/passwd", outputStream));
            }
        }
    }

    @Test
    void testStreamFile_ContainerXmlAllowed() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            byte[] containerContent = CONTAINER_XML.getBytes(StandardCharsets.UTF_8);
            ZipFile zipFile = createMockZipFileWithStreamableEntry("META-INF/container.xml", containerContent);
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                // Reset mock for streaming call
                reset(builder);
                when(builder.setPath(epubPath)).thenReturn(builder);
                when(builder.setCharset(any(Charset.class))).thenReturn(builder);
                when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
                when(builder.get()).thenReturn(zipFile);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                epubReaderService.streamFile(1L, "META-INF/container.xml", outputStream);

                assertArrayEquals(containerContent, outputStream.toByteArray());
            }
        }
    }

    @Test
    void testGetContentType_FromManifest() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                assertEquals("application/xhtml+xml", epubReaderService.getContentType(1L, "chapter1.xhtml"));
                assertEquals("text/css", epubReaderService.getContentType(1L, "style.css"));
                assertEquals("image/jpeg", epubReaderService.getContentType(1L, "cover.jpg"));
            }
        }
    }

    @Test
    void testGetContentType_Fallback() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                // Unknown file should use fallback guessing
                assertEquals("image/png", epubReaderService.getContentType(1L, "image.png"));
                assertEquals("image/gif", epubReaderService.getContentType(1L, "animation.gif"));
                assertEquals("font/woff2", epubReaderService.getContentType(1L, "font.woff2"));
                assertEquals("application/octet-stream", epubReaderService.getContentType(1L, "unknown.xyz"));
            }
        }
    }

    @Test
    void testGetFileSize_FromManifest() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFileWithSizes();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                assertEquals(1024L, epubReaderService.getFileSize(1L, "chapter1.xhtml"));
                assertEquals(2048L, epubReaderService.getFileSize(1L, "chapter2.xhtml"));
            }
        }
    }

    @Test
    void testGetFileSize_NotFound() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                assertEquals(0L, epubReaderService.getFileSize(1L, "nonexistent.xhtml"));
            }
        }
    }

    @Test
    void testSpineOrder() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

                List<EpubSpineItem> spine = bookInfo.getSpine();
                assertEquals(2, spine.size());
                assertEquals("chapter1", spine.get(0).getIdref());
                assertEquals("OEBPS/chapter1.xhtml", spine.get(0).getHref());
                assertEquals("chapter2", spine.get(1).getIdref());
                assertEquals("OEBPS/chapter2.xhtml", spine.get(1).getHref());
            }
        }
    }

    @Test
    void testTocParsing() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

                EpubTocItem toc = bookInfo.getToc();
                assertNotNull(toc);
                assertEquals("Table of Contents", toc.getLabel());
                assertNotNull(toc.getChildren());
                assertEquals(2, toc.getChildren().size());
                assertEquals("Chapter 1", toc.getChildren().get(0).getLabel());
                assertEquals("Chapter 2", toc.getChildren().get(1).getLabel());
            }
        }
    }

    @Test
    void testManifestProperties() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            ZipFile zipFile = createMockZipFile();
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

                // Find cover item
                EpubManifestItem coverItem = bookInfo.getManifest().stream()
                        .filter(item -> "cover".equals(item.getId()))
                        .findFirst()
                        .orElse(null);

                assertNotNull(coverItem);
                assertNotNull(coverItem.getProperties());
                assertTrue(coverItem.getProperties().contains("cover-image"));

                // Find nav item
                EpubManifestItem navItem = bookInfo.getManifest().stream()
                        .filter(item -> "nav".equals(item.getId()))
                        .findFirst()
                        .orElse(null);

                assertNotNull(navItem);
                assertNotNull(navItem.getProperties());
                assertTrue(navItem.getProperties().contains("nav"));
            }
        }
    }

    @Test
    void testStreamFile_LeadingSlashHandled() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath.toString());

            byte[] chapterContent = "<html><body>Chapter 1 content</body></html>".getBytes(StandardCharsets.UTF_8);
            ZipFile zipFile = createMockZipFileWithStreamableEntry("OEBPS/chapter1.xhtml", chapterContent);
            ZipFile.Builder builder = createMockZipFileBuilder(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(epubPath);
                Files.setLastModifiedTime(epubPath, FileTime.fromMillis(System.currentTimeMillis()));

                // First populate the cache
                epubReaderService.getBookInfo(1L);

                // Reset mock for streaming call
                reset(builder);
                when(builder.setPath(epubPath)).thenReturn(builder);
                when(builder.setCharset(any(Charset.class))).thenReturn(builder);
                when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
                when(builder.get()).thenReturn(zipFile);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                // Path with leading slash should work
                epubReaderService.streamFile(1L, "/chapter1.xhtml", outputStream);

                assertArrayEquals(chapterContent, outputStream.toByteArray());
            }
        }
    }

    // Helper methods to create mock ZipFile objects

    private ZipFile createMockZipFile() throws Exception {
        ZipFile zipFile = mock(ZipFile.class);

        // Container entry - use thenAnswer to return fresh stream each time
        ZipArchiveEntry containerEntry = mock(ZipArchiveEntry.class);
        when(containerEntry.getSize()).thenReturn((long) CONTAINER_XML.length());
        when(zipFile.getEntry("META-INF/container.xml")).thenReturn(containerEntry);
        when(zipFile.getInputStream(containerEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(CONTAINER_XML.getBytes(StandardCharsets.UTF_8)));

        // OPF entry
        ZipArchiveEntry opfEntry = mock(ZipArchiveEntry.class);
        when(opfEntry.getSize()).thenReturn((long) CONTENT_OPF.length());
        when(zipFile.getEntry("OEBPS/content.opf")).thenReturn(opfEntry);
        when(zipFile.getInputStream(opfEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(CONTENT_OPF.getBytes(StandardCharsets.UTF_8)));

        // Nav entry
        ZipArchiveEntry navEntry = mock(ZipArchiveEntry.class);
        when(navEntry.getSize()).thenReturn((long) NAV_XHTML.length());
        when(zipFile.getEntry("OEBPS/nav.xhtml")).thenReturn(navEntry);
        when(zipFile.getInputStream(navEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(NAV_XHTML.getBytes(StandardCharsets.UTF_8)));

        // Chapter entries with sizes
        ZipArchiveEntry chapter1Entry = mock(ZipArchiveEntry.class);
        when(chapter1Entry.getSize()).thenReturn(100L);
        when(zipFile.getEntry("OEBPS/chapter1.xhtml")).thenReturn(chapter1Entry);

        ZipArchiveEntry chapter2Entry = mock(ZipArchiveEntry.class);
        when(chapter2Entry.getSize()).thenReturn(200L);
        when(zipFile.getEntry("OEBPS/chapter2.xhtml")).thenReturn(chapter2Entry);

        ZipArchiveEntry styleEntry = mock(ZipArchiveEntry.class);
        when(styleEntry.getSize()).thenReturn(50L);
        when(zipFile.getEntry("OEBPS/style.css")).thenReturn(styleEntry);

        ZipArchiveEntry coverEntry = mock(ZipArchiveEntry.class);
        when(coverEntry.getSize()).thenReturn(5000L);
        when(zipFile.getEntry("OEBPS/cover.jpg")).thenReturn(coverEntry);

        return zipFile;
    }

    private ZipFile createMockZipFileWithStreamableEntry(String entryPath, byte[] content) throws Exception {
        ZipFile zipFile = createMockZipFile();

        ZipArchiveEntry entry = mock(ZipArchiveEntry.class);
        when(entry.getSize()).thenReturn((long) content.length);
        when(zipFile.getEntry(entryPath)).thenReturn(entry);
        when(zipFile.getInputStream(entry)).thenAnswer(inv -> new ByteArrayInputStream(content));

        return zipFile;
    }

    private ZipFile createMockZipFileWithSizes() throws Exception {
        ZipFile zipFile = mock(ZipFile.class);

        // Container entry - use thenAnswer for fresh stream each time
        ZipArchiveEntry containerEntry = mock(ZipArchiveEntry.class);
        when(containerEntry.getSize()).thenReturn((long) CONTAINER_XML.length());
        when(zipFile.getEntry("META-INF/container.xml")).thenReturn(containerEntry);
        when(zipFile.getInputStream(containerEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(CONTAINER_XML.getBytes(StandardCharsets.UTF_8)));

        // OPF entry
        ZipArchiveEntry opfEntry = mock(ZipArchiveEntry.class);
        when(opfEntry.getSize()).thenReturn((long) CONTENT_OPF.length());
        when(zipFile.getEntry("OEBPS/content.opf")).thenReturn(opfEntry);
        when(zipFile.getInputStream(opfEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(CONTENT_OPF.getBytes(StandardCharsets.UTF_8)));

        // Nav entry
        ZipArchiveEntry navEntry = mock(ZipArchiveEntry.class);
        when(navEntry.getSize()).thenReturn((long) NAV_XHTML.length());
        when(zipFile.getEntry("OEBPS/nav.xhtml")).thenReturn(navEntry);
        when(zipFile.getInputStream(navEntry))
                .thenAnswer(inv -> new ByteArrayInputStream(NAV_XHTML.getBytes(StandardCharsets.UTF_8)));

        // Chapter entries with specific sizes
        ZipArchiveEntry chapter1Entry = mock(ZipArchiveEntry.class);
        when(chapter1Entry.getSize()).thenReturn(1024L);
        when(zipFile.getEntry("OEBPS/chapter1.xhtml")).thenReturn(chapter1Entry);

        ZipArchiveEntry chapter2Entry = mock(ZipArchiveEntry.class);
        when(chapter2Entry.getSize()).thenReturn(2048L);
        when(zipFile.getEntry("OEBPS/chapter2.xhtml")).thenReturn(chapter2Entry);

        ZipArchiveEntry styleEntry = mock(ZipArchiveEntry.class);
        when(styleEntry.getSize()).thenReturn(512L);
        when(zipFile.getEntry("OEBPS/style.css")).thenReturn(styleEntry);

        ZipArchiveEntry coverEntry = mock(ZipArchiveEntry.class);
        when(coverEntry.getSize()).thenReturn(10240L);
        when(zipFile.getEntry("OEBPS/cover.jpg")).thenReturn(coverEntry);

        return zipFile;
    }

    private ZipFile.Builder createMockZipFileBuilder(ZipFile zipFile) throws Exception {
        ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.setPath(epubPath)).thenReturn(builder);
        when(builder.setCharset(any(Charset.class))).thenReturn(builder);
        when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
        when(builder.get()).thenReturn(zipFile);
        return builder;
    }
}
