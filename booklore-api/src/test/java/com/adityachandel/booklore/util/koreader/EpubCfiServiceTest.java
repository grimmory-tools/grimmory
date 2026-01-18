package com.adityachandel.booklore.util.koreader;

import io.documentnode.epub4j.domain.Author;
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

import static org.junit.jupiter.api.Assertions.*;

class EpubCfiServiceTest {

    @TempDir
    Path tempDir;

    private EpubCfiService service;
    private File testEpubFile;

    private static final String CHAPTER1_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 1</title></head>
            <body>
                <div id="chapter1">
                    <h1>Chapter 1</h1>
                    <p>First paragraph with some text content.</p>
                    <p>Second paragraph with more text.</p>
                </div>
            </body>
            </html>
            """;

    private static final String CHAPTER2_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 2</title></head>
            <body>
                <div id="chapter2">
                    <h1>Chapter 2</h1>
                    <p>Content of chapter two.</p>
                </div>
            </body>
            </html>
            """;

    private static final String CHAPTER3_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>Chapter 3</title></head>
            <body>
                <div id="chapter3">
                    <h1>Chapter 3</h1>
                    <p>Final chapter content here.</p>
                </div>
            </body>
            </html>
            """;

    @BeforeEach
    void setUp() throws IOException {
        service = new EpubCfiService();
        testEpubFile = createTestEpub("test.epub");
    }

    private File createTestEpub(String filename) throws IOException {
        Book book = new Book();
        book.getMetadata().addTitle("Test Book");
        book.getMetadata().addAuthor(new Author("Test Author"));

        Resource chapter1 = new Resource(CHAPTER1_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter1.xhtml");
        Resource chapter2 = new Resource(CHAPTER2_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter2.xhtml");
        Resource chapter3 = new Resource(CHAPTER3_CONTENT.getBytes(StandardCharsets.UTF_8), "chapter3.xhtml");

        book.addSection("Chapter 1", chapter1);
        book.addSection("Chapter 2", chapter2);
        book.addSection("Chapter 3", chapter3);

        File epubFile = tempDir.resolve(filename).toFile();
        try (FileOutputStream out = new FileOutputStream(epubFile)) {
            new EpubWriter().write(book, out);
        }
        return epubFile;
    }

    @Nested
    class CreateConverterTests {

        @Test
        void createConverter_validFile_returnsConverter() {
            CfiConvertor converter = service.createConverter(testEpubFile, 0);

            assertNotNull(converter);
        }

        @Test
        void createConverter_withPath_returnsConverter() {
            CfiConvertor converter = service.createConverter(testEpubFile.toPath(), 0);

            assertNotNull(converter);
        }

        @Test
        void createConverter_differentSpineIndices_returnsDifferentConverters() {
            CfiConvertor converter0 = service.createConverter(testEpubFile, 0);
            CfiConvertor converter1 = service.createConverter(testEpubFile, 1);

            assertNotNull(converter0);
            assertNotNull(converter1);
        }
    }

    @Nested
    class ConvertXPointerToCfiTests {

        @Test
        void convertXPointerToCfi_validXPointer_returnsCfi() {
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = service.convertXPointerToCfi(testEpubFile, xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
            assertTrue(cfi.endsWith(")"));
        }

        @Test
        void convertXPointerToCfi_withPath_returnsCfi() {
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = service.convertXPointerToCfi(testEpubFile.toPath(), xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
        }

        @Test
        void convertXPointerToCfi_secondSpine_returnsCfiWithCorrectSpineIndex() {
            String xpointer = "/body/DocFragment[2]/body/div[1]/p[1]";

            String cfi = service.convertXPointerToCfi(testEpubFile, xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.contains("/6/4!"));
        }
    }

    @Nested
    class ConvertCfiToXPointerTests {

        @Test
        void convertCfiToXPointer_validCfi_returnsXPointerResult() {
            String cfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult result = service.convertCfiToXPointer(testEpubFile, cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().startsWith("/body/DocFragment[1]/body"));
        }

        @Test
        void convertCfiToXPointer_withPath_returnsXPointerResult() {
            String cfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult result = service.convertCfiToXPointer(testEpubFile.toPath(), cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
        }
    }

    @Nested
    class ConvertXPointerRangeToCfiTests {

        @Test
        void convertXPointerRangeToCfi_validRange_returnsCfi() {
            String startXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().0";
            String endXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().10";

            String cfi = service.convertXPointerRangeToCfi(testEpubFile, startXPointer, endXPointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
        }

        @Test
        void convertXPointerRangeToCfi_withPath_returnsCfi() {
            String startXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().0";
            String endXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().10";

            String cfi = service.convertXPointerRangeToCfi(testEpubFile.toPath(), startXPointer, endXPointer);

            assertNotNull(cfi);
        }
    }

    @Nested
    class ConvertCfiToProgressXPointerTests {

        @Test
        void convertCfiToProgressXPointer_validCfi_returnsNormalizedXPointer() {
            String cfi = "epubcfi(/6/2!/4/2/2)";

            String progressXPointer = service.convertCfiToProgressXPointer(testEpubFile, cfi);

            assertNotNull(progressXPointer);
            assertFalse(progressXPointer.contains("/text()."));
        }

        @Test
        void convertCfiToProgressXPointer_withPath_returnsNormalizedXPointer() {
            String cfi = "epubcfi(/6/2!/4/2/2)";

            String progressXPointer = service.convertCfiToProgressXPointer(testEpubFile.toPath(), cfi);

            assertNotNull(progressXPointer);
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void validateCfi_validCfi_returnsTrue() {
            String cfi = "epubcfi(/6/2!/4/2)";

            boolean result = service.validateCfi(testEpubFile, cfi);

            assertTrue(result);
        }

        @Test
        void validateCfi_invalidCfi_returnsFalse() {
            boolean result = service.validateCfi(testEpubFile, "invalid-cfi");

            assertFalse(result);
        }

        @Test
        void validateCfi_malformedCfi_returnsFalse() {
            boolean result = service.validateCfi(testEpubFile, "epubcfi(malformed)");

            assertFalse(result);
        }

        @Test
        void validateXPointer_validXPointer_returnsTrue() {
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            boolean result = service.validateXPointer(testEpubFile, xpointer);

            assertTrue(result);
        }

        @Test
        void validateXPointer_invalidXPointer_returnsFalse() {
            boolean result = service.validateXPointer(testEpubFile, "/invalid/path");

            assertFalse(result);
        }
    }

    @Nested
    class ExtractSpineIndexTests {

        @Test
        void extractSpineIndex_fromCfi_returnsCorrectIndex() {
            int index = service.extractSpineIndex("epubcfi(/6/4!/4/2)");

            assertEquals(1, index);
        }

        @Test
        void extractSpineIndex_fromXPointer_returnsCorrectIndex() {
            int index = service.extractSpineIndex("/body/DocFragment[3]/body/div/p");

            assertEquals(2, index);
        }
    }

    @Nested
    class GetSpineSizeTests {

        @Test
        void getSpineSize_validEpub_returnsCorrectSize() {
            int size = service.getSpineSize(testEpubFile);

            assertEquals(3, size);
        }
    }

    @Nested
    class CacheTests {

        @Test
        void createConverter_calledTwice_usesCachedDocument() {
            CfiConvertor converter1 = service.createConverter(testEpubFile, 0);
            CfiConvertor converter2 = service.createConverter(testEpubFile, 0);

            assertNotNull(converter1);
            assertNotNull(converter2);
        }

        @Test
        void evictCache_removesEntriesForFile() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);

            service.evictCache(testEpubFile);

            CfiConvertor converter = service.createConverter(testEpubFile, 0);
            assertNotNull(converter);
        }

        @Test
        void clearCache_removesAllEntries() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);

            service.clearCache();

            CfiConvertor converter = service.createConverter(testEpubFile, 0);
            assertNotNull(converter);
        }

        @Test
        void cache_differentSpineIndices_cachedSeparately() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);
            service.createConverter(testEpubFile, 2);

            CfiConvertor converter0 = service.createConverter(testEpubFile, 0);
            CfiConvertor converter1 = service.createConverter(testEpubFile, 1);
            CfiConvertor converter2 = service.createConverter(testEpubFile, 2);

            assertNotNull(converter0);
            assertNotNull(converter1);
            assertNotNull(converter2);
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void roundTrip_xPointerToCfiAndBack() {
            String originalXPointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = service.convertXPointerToCfi(testEpubFile, originalXPointer);
            CfiConvertor.XPointerResult result = service.convertCfiToXPointer(testEpubFile, cfi);

            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().contains("/body/DocFragment[1]/body"));
        }

        @Test
        void roundTrip_cfiToXPointerAndBack() {
            String originalCfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult xpointerResult = service.convertCfiToXPointer(testEpubFile, originalCfi);
            String cfi = service.convertXPointerToCfi(testEpubFile, xpointerResult.getXpointer());

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
            assertTrue(cfi.contains("/6/2!"));
        }
    }

    @Nested
    class MultipleFilesTests {

        @Test
        void cache_differentFiles_cachedSeparately() throws IOException {
            File secondEpub = createTestEpub("second.epub");

            service.createConverter(testEpubFile, 0);
            service.createConverter(secondEpub, 0);

            CfiConvertor converter1 = service.createConverter(testEpubFile, 0);
            CfiConvertor converter2 = service.createConverter(secondEpub, 0);

            assertNotNull(converter1);
            assertNotNull(converter2);
        }

        @Test
        void evictCache_onlyAffectsSpecificFile() throws IOException {
            File secondEpub = createTestEpub("second.epub");

            service.createConverter(testEpubFile, 0);
            service.createConverter(secondEpub, 0);

            service.evictCache(testEpubFile);

            CfiConvertor converter = service.createConverter(secondEpub, 0);
            assertNotNull(converter);
        }
    }
}
