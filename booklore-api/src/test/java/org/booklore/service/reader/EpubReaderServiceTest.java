package org.booklore.service.reader;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.model.dto.response.EpubManifestItem;
import org.booklore.model.dto.response.EpubSpineItem;
import org.booklore.model.dto.response.EpubTocItem;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.epub.EpubWriter;
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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @BeforeEach
    void setup() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        epubPath = tempDir.resolve("test.epub");
        Files.deleteIfExists(epubPath);
    }

    private void writeTestEpub() throws Exception {
        Book book = new Book();
        Metadata metadata = book.getMetadata();
        metadata.addTitle("Test Book");
        metadata.addAuthor(new Author("Test", "Author"));
        metadata.setLanguage("en");

        Resource chapter1 = new Resource(
                "<html><body>Chapter 1 content</body></html>".getBytes(StandardCharsets.UTF_8),
                "chapter1.xhtml");
        chapter1.setId("chapter1");
        Resource chapter2 = new Resource(
                "<html><body>Chapter 2 content</body></html>".getBytes(StandardCharsets.UTF_8),
                "chapter2.xhtml");
        chapter2.setId("chapter2");
        Resource style = new Resource("body { color: black; }".getBytes(StandardCharsets.UTF_8), "style.css");
        style.setId("style");

        byte[] coverData = new byte[5000];
        Resource cover = new Resource(coverData, "cover.jpg");
        cover.setId("cover");
        book.setCoverImage(cover);

        book.addSection("Chapter 1", chapter1);
        book.addSection("Chapter 2", chapter2);
        book.getResources().add(style);

        try (FileOutputStream fos = new FileOutputStream(epubPath.toFile())) {
            new EpubWriter().write(book, fos);
        }
    }

    @Test
    void testGetBookInfo_Success() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            assertNotNull(bookInfo);
            assertNotNull(bookInfo.getContainerPath());
            assertNotNull(bookInfo.getRootPath());
            assertEquals("Test Book", bookInfo.getMetadata().get("title"));
            assertEquals("Test Author", bookInfo.getMetadata().get("creator"));
            assertEquals("en", bookInfo.getMetadata().get("language"));
            assertFalse(bookInfo.getManifest().isEmpty());
            assertEquals(2, bookInfo.getSpine().size());
            assertNotNull(bookInfo.getCoverPath());
        }
    }

    @Test
    void testGetBookInfo_BookNotFound() {
        when(bookRepository.findByIdForStreaming(999L)).thenReturn(Optional.empty());

        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(),
                () -> epubReaderService.getBookInfo(999L));
    }

    @Test
    void testGetBookInfo_CacheHit() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First call - should parse
            EpubBookInfo bookInfo1 = epubReaderService.getBookInfo(1L);
            // Second call - should use cache
            EpubBookInfo bookInfo2 = epubReaderService.getBookInfo(1L);

            assertSame(bookInfo1, bookInfo2);
        }
    }

    @Test
    void testStreamFile_Success() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            // Find the chapter1 href from the manifest
            String chapter1Href = bookInfo.getManifest().stream()
                    .filter(m -> "chapter1".equals(m.getId()))
                    .map(EpubManifestItem::getHref)
                    .findFirst()
                    .orElseThrow();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            epubReaderService.streamFile(1L, chapter1Href, outputStream);

            String content = outputStream.toString(StandardCharsets.UTF_8);
            assertTrue(content.contains("Chapter 1 content"));
        }
    }

    @Test
    void testStreamFile_FileNotFound() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            epubReaderService.getBookInfo(1L);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            assertThrows(FileNotFoundException.class,
                    () -> epubReaderService.streamFile(1L, "nonexistent.xhtml", outputStream));
        }
    }

    @Test
    void testStreamFile_PathTraversalBlocked() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            epubReaderService.getBookInfo(1L);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            assertThrows(FileNotFoundException.class,
                    () -> epubReaderService.streamFile(1L, "../../../etc/passwd", outputStream));
        }
    }

    @Test
    void testStreamFile_ContainerXmlAllowed() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            epubReaderService.getBookInfo(1L);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            epubReaderService.streamFile(1L, "META-INF/container.xml", outputStream);

            String content = outputStream.toString(StandardCharsets.UTF_8);
            assertTrue(content.contains("rootfile"));
        }
    }

    @Test
    void testGetContentType_FromManifest() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            // Find chapter1 href
            String chapter1Href = bookInfo.getManifest().stream()
                    .filter(m -> "chapter1".equals(m.getId()))
                    .map(EpubManifestItem::getHref)
                    .findFirst()
                    .orElseThrow();

            assertEquals("application/xhtml+xml", epubReaderService.getContentType(1L, chapter1Href));
        }
    }

    @Test
    void testGetContentType_Fallback() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            epubReaderService.getBookInfo(1L);

            // Unknown file should use fallback guessing
            assertEquals("image/png", epubReaderService.getContentType(1L, "image.png"));
            assertEquals("image/gif", epubReaderService.getContentType(1L, "animation.gif"));
            assertEquals("font/woff2", epubReaderService.getContentType(1L, "font.woff2"));
            assertEquals("application/octet-stream", epubReaderService.getContentType(1L, "unknown.xyz"));
        }
    }

    @Test
    void testGetFileSize_FromManifest() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            // Find chapter1 href and verify size > 0
            String chapter1Href = bookInfo.getManifest().stream()
                    .filter(m -> "chapter1".equals(m.getId()))
                    .map(EpubManifestItem::getHref)
                    .findFirst()
                    .orElseThrow();

            assertTrue(epubReaderService.getFileSize(1L, chapter1Href) > 0);
        }
    }

    @Test
    void testGetFileSize_NotFound() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            assertEquals(0L, epubReaderService.getFileSize(1L, "nonexistent.xhtml"));
        }
    }

    @Test
    void testSpineOrder() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            List<EpubSpineItem> spine = bookInfo.getSpine();
            assertEquals(2, spine.size());
            assertEquals("chapter1", spine.get(0).getIdref());
            assertTrue(spine.get(0).getHref().contains("chapter1"));
            assertEquals("chapter2", spine.get(1).getIdref());
            assertTrue(spine.get(1).getHref().contains("chapter2"));
        }
    }

    @Test
    void testTocParsing() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

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

    @Test
    void testManifestProperties() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            // Find cover item - should have cover-image property
            EpubManifestItem coverItem = bookInfo.getManifest().stream()
                    .filter(item -> "cover".equals(item.getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(coverItem);
            assertNotNull(coverItem.getProperties());
            assertTrue(coverItem.getProperties().contains("cover-image"));
        }
    }

    @Test
    void testStreamFile_LeadingSlashHandled() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(epubPath);

            writeTestEpub();

            // First populate the cache
            EpubBookInfo bookInfo = epubReaderService.getBookInfo(1L);

            // Find the chapter1 relative path (without rootPath prefix)
            String chapter1Href = bookInfo.getManifest().stream()
                    .filter(m -> "chapter1".equals(m.getId()))
                    .map(EpubManifestItem::getHref)
                    .findFirst()
                    .orElseThrow();

            // Strip rootPath to get relative path, then add leading slash
            String rootPath = bookInfo.getRootPath();
            String relativePath = chapter1Href.startsWith(rootPath)
                    ? chapter1Href.substring(rootPath.length())
                    : chapter1Href;

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            // Path with leading slash should work
            epubReaderService.streamFile(1L, "/" + relativePath, outputStream);

            String content = outputStream.toString(StandardCharsets.UTF_8);
            assertTrue(content.contains("Chapter 1 content"));
        }
    }
}
