package com.adityachandel.booklore.util.epub;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EpubContentReaderTest {

    @TempDir
    Path tempDir;

    private File validEpubFile;
    private File emptySpineEpubFile;

    private static final String CHAPTER1_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
                <h1>Chapter 1</h1>
                <p>This is the first paragraph of chapter one.</p>
                <p>This is the second paragraph.</p>
            </body>
            </html>
            """;

    private static final String CHAPTER2_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 2</title></head>
            <body>
                <h1>Chapter 2</h1>
                <p>Content of chapter two.</p>
            </body>
            </html>
            """;

    private static final String CHAPTER3_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 3</title></head>
            <body>
                <h1>Chapter 3</h1>
                <p>Final chapter content.</p>
            </body>
            </html>
            """;

    @BeforeEach
    void setUp() throws IOException {
        validEpubFile = createTestEpub("valid.epub", true);
        emptySpineEpubFile = createTestEpub("empty_spine.epub", false);
    }

    private File createTestEpub(String filename, boolean withSpine) throws IOException {
        Book book = new Book();
        book.getMetadata().addTitle("Test Book");
        book.getMetadata().addAuthor(new io.documentnode.epub4j.domain.Author("Test Author"));

        if (withSpine) {
            Resource chapter1 = new Resource(CHAPTER1_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml");
            Resource chapter2 = new Resource(CHAPTER2_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter2.xhtml");
            Resource chapter3 = new Resource(CHAPTER3_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter3.xhtml");

            book.addSection("Chapter 1", chapter1);
            book.addSection("Chapter 2", chapter2);
            book.addSection("Chapter 3", chapter3);
        }

        File epubFile = tempDir.resolve(filename).toFile();
        try (FileOutputStream out = new FileOutputStream(epubFile)) {
            new EpubWriter().write(book, out);
        }
        return epubFile;
    }

    @Nested
    class GetSpineItemContentTests {

        @Test
        void getSpineItemContent_firstChapter_returnsContent() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile, 0);

            assertNotNull(content);
            assertTrue(content.contains("Chapter 1"));
            assertTrue(content.contains("first paragraph"));
        }

        @Test
        void getSpineItemContent_secondChapter_returnsContent() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile, 1);

            assertNotNull(content);
            assertTrue(content.contains("Chapter 2"));
            assertTrue(content.contains("chapter two"));
        }

        @Test
        void getSpineItemContent_lastChapter_returnsContent() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile, 2);

            assertNotNull(content);
            assertTrue(content.contains("Chapter 3"));
            assertTrue(content.contains("Final chapter"));
        }

        @Test
        void getSpineItemContent_withPath_returnsContent() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile.toPath(), 0);

            assertNotNull(content);
            assertTrue(content.contains("Chapter 1"));
        }

        @Test
        void getSpineItemContent_negativeIndex_throwsException() {
            EpubContentReader.EpubReadException exception = assertThrows(
                    EpubContentReader.EpubReadException.class,
                    () -> EpubContentReader.getSpineItemContent(validEpubFile, -1)
            );

            assertTrue(exception.getMessage().contains("out of bounds"));
        }

        @Test
        void getSpineItemContent_indexTooLarge_throwsException() {
            EpubContentReader.EpubReadException exception = assertThrows(
                    EpubContentReader.EpubReadException.class,
                    () -> EpubContentReader.getSpineItemContent(validEpubFile, 999)
            );

            assertTrue(exception.getMessage().contains("out of bounds"));
        }

        @Test
        void getSpineItemContent_emptySpine_throwsException() {
            EpubContentReader.EpubReadException exception = assertThrows(
                    EpubContentReader.EpubReadException.class,
                    () -> EpubContentReader.getSpineItemContent(emptySpineEpubFile, 0)
            );

            assertTrue(exception.getMessage().contains("no spine"));
        }

        @Test
        void getSpineItemContent_nonExistentFile_throwsException() {
            File nonExistent = new File(tempDir.toFile(), "nonexistent.epub");

            assertThrows(
                    EpubContentReader.EpubReadException.class,
                    () -> EpubContentReader.getSpineItemContent(nonExistent, 0)
            );
        }

        @Test
        void getSpineItemContent_invalidEpubFile_throwsException() throws IOException {
            File invalidFile = tempDir.resolve("invalid.epub").toFile();
            java.nio.file.Files.writeString(invalidFile.toPath(), "not an epub");

            assertThrows(
                    EpubContentReader.EpubReadException.class,
                    () -> EpubContentReader.getSpineItemContent(invalidFile, 0)
            );
        }
    }

    @Nested
    class GetSpineSizeTests {

        @Test
        void getSpineSize_validEpub_returnsCorrectSize() {
            int size = EpubContentReader.getSpineSize(validEpubFile);

            assertEquals(3, size);
        }

        @Test
        void getSpineSize_emptySpine_returnsZero() {
            int size = EpubContentReader.getSpineSize(emptySpineEpubFile);

            assertEquals(0, size);
        }

        @Test
        void getSpineSize_nonExistentFile_returnsZero() {
            File nonExistent = new File(tempDir.toFile(), "nonexistent.epub");

            int size = EpubContentReader.getSpineSize(nonExistent);

            assertEquals(0, size);
        }
    }

    @Nested
    class GetSpineItemHrefTests {

        @Test
        void getSpineItemHref_validIndex_returnsHref() {
            String href = EpubContentReader.getSpineItemHref(validEpubFile, 0);

            assertNotNull(href);
            assertTrue(href.contains("chapter1"));
        }

        @Test
        void getSpineItemHref_secondItem_returnsCorrectHref() {
            String href = EpubContentReader.getSpineItemHref(validEpubFile, 1);

            assertNotNull(href);
            assertTrue(href.contains("chapter2"));
        }

        @Test
        void getSpineItemHref_negativeIndex_returnsNull() {
            String href = EpubContentReader.getSpineItemHref(validEpubFile, -1);

            assertNull(href);
        }

        @Test
        void getSpineItemHref_indexTooLarge_returnsNull() {
            String href = EpubContentReader.getSpineItemHref(validEpubFile, 999);

            assertNull(href);
        }

        @Test
        void getSpineItemHref_emptySpine_returnsNull() {
            String href = EpubContentReader.getSpineItemHref(emptySpineEpubFile, 0);

            assertNull(href);
        }
    }

    @Nested
    class GetAllSpineItemHrefsTests {

        @Test
        void getAllSpineItemHrefs_validEpub_returnsAllHrefs() {
            List<String> hrefs = EpubContentReader.getAllSpineItemHrefs(validEpubFile);

            assertEquals(3, hrefs.size());
            assertTrue(hrefs.get(0).contains("chapter1"));
            assertTrue(hrefs.get(1).contains("chapter2"));
            assertTrue(hrefs.get(2).contains("chapter3"));
        }

        @Test
        void getAllSpineItemHrefs_emptySpine_returnsEmptyList() {
            List<String> hrefs = EpubContentReader.getAllSpineItemHrefs(emptySpineEpubFile);

            assertTrue(hrefs.isEmpty());
        }

        @Test
        void getAllSpineItemHrefs_nonExistentFile_returnsEmptyList() {
            File nonExistent = new File(tempDir.toFile(), "nonexistent.epub");

            List<String> hrefs = EpubContentReader.getAllSpineItemHrefs(nonExistent);

            assertTrue(hrefs.isEmpty());
        }
    }

    @Nested
    class EpubReadExceptionTests {

        @Test
        void epubReadException_withMessage_containsMessage() {
            EpubContentReader.EpubReadException exception =
                    new EpubContentReader.EpubReadException("Test message");

            assertEquals("Test message", exception.getMessage());
        }

        @Test
        void epubReadException_withCause_containsCause() {
            IOException cause = new IOException("IO error");
            EpubContentReader.EpubReadException exception =
                    new EpubContentReader.EpubReadException("Test message", cause);

            assertEquals("Test message", exception.getMessage());
            assertEquals(cause, exception.getCause());
        }
    }

    @Nested
    class ContentValidationTests {

        @Test
        void getSpineItemContent_returnsUtf8Content() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile, 0);

            assertNotNull(content);
            assertFalse(content.isEmpty());
            assertTrue(content.contains("<body>") || content.contains("<BODY>") || content.toLowerCase().contains("<body"));
        }

        @Test
        void getSpineItemContent_preservesHtmlStructure() {
            String content = EpubContentReader.getSpineItemContent(validEpubFile, 0);

            assertTrue(content.contains("<h1>") || content.toLowerCase().contains("<h1"));
            assertTrue(content.contains("<p>") || content.toLowerCase().contains("<p"));
        }

        @Test
        void getSpineItemContent_multipleReadsReturnSameContent() {
            String content1 = EpubContentReader.getSpineItemContent(validEpubFile, 0);
            String content2 = EpubContentReader.getSpineItemContent(validEpubFile, 0);

            assertEquals(content1, content2);
        }
    }
}
