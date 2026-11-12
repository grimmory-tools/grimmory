package org.booklore.util.koreader;

import org.booklore.exception.APIException;
import org.grimmory.epub4j.domain.Author;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.EpubWriter;
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
            CfiConverter converter = service.createConverter(testEpubFile, 0);

            assertNotNull(converter);
        }

        @Test
        void createConverter_withPath_returnsConverter() {
            CfiConverter converter = service.createConverter(testEpubFile.toPath(), 0);

            assertNotNull(converter);
        }

        @Test
        void createConverter_differentSpineIndices_returnsDifferentConverters() {
            CfiConverter converter0 = service.createConverter(testEpubFile, 0);
            CfiConverter converter1 = service.createConverter(testEpubFile, 1);

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

            XPointerResult result = service.convertCfiToXPointer(testEpubFile, cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().startsWith("/body/DocFragment[1]/body"));
        }

        @Test
        void convertCfiToXPointer_withPath_returnsXPointerResult() {
            String cfi = "epubcfi(/6/2!/4/2/2)";

            XPointerResult result = service.convertCfiToXPointer(testEpubFile.toPath(), cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
        }

        @Test
        void convertCfiToXPointer_roundTripsNestedParagraphLocation() {
            String originalXPointer = "/body/DocFragment[1]/body/div[1]/p[2]/text().10";
            String cfi = service.convertXPointerToCfi(testEpubFile, originalXPointer);

            XPointerResult result = service.convertCfiToXPointer(testEpubFile, cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().contains("/p[2]/text().10"), result.getXpointer());
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

        @Test
        void convertXPointerRangeToCfi_mismatchedSpineIndices_throwsAPIException() {
            String startXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().0";
            String endXPointer = "/body/DocFragment[2]/body/div[1]/p[1]/text().10";

            assertThrows(APIException.class, () ->
                    service.convertXPointerRangeToCfi(testEpubFile, startXPointer, endXPointer));
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
    class ResolveCfiLocationTests {

        @Test
        void resolveCfiLocation_textOffsetsReturnMatchingHrefAndIncreasingProgress() {
            String earlyCfi = service.convertXPointerToCfi(testEpubFile, "/body/DocFragment[1]/body/div[1]/p[1]/text().0");
            String laterCfi = service.convertXPointerToCfi(testEpubFile, "/body/DocFragment[1]/body/div[1]/p[2]/text().10");

            EpubCfiService.CfiLocation earlyLocation = service.resolveCfiLocation(testEpubFile, earlyCfi).orElseThrow();
            EpubCfiService.CfiLocation laterLocation = service.resolveCfiLocation(testEpubFile, laterCfi).orElseThrow();

            assertEquals("chapter1.xhtml", earlyLocation.href());
            assertEquals("chapter1.xhtml", laterLocation.href());
            assertNotNull(earlyLocation.contentSourceProgressPercent());
            assertNotNull(laterLocation.contentSourceProgressPercent());
            assertTrue(earlyLocation.contentSourceProgressPercent() > 0f);
            assertTrue(earlyLocation.contentSourceProgressPercent() < laterLocation.contentSourceProgressPercent());
        }

        @Test
        void resolveCfiLocation_webReaderStyleTerminalTextStepResolvesDeepIntoChapter() {
            String earlyCfi = "epubcfi(/6/2!/4/2/2/1:0)";
            String laterCfi = "epubcfi(/6/2!/4/2/4/1:10)";

            EpubCfiService.CfiLocation earlyLocation = service.resolveCfiLocation(testEpubFile, earlyCfi).orElseThrow();
            EpubCfiService.CfiLocation laterLocation = service.resolveCfiLocation(testEpubFile, laterCfi).orElseThrow();

            assertEquals("chapter1.xhtml", earlyLocation.href());
            assertEquals("chapter1.xhtml", laterLocation.href());
            assertNotNull(earlyLocation.contentSourceProgressPercent());
            assertNotNull(laterLocation.contentSourceProgressPercent());
            assertTrue(earlyLocation.contentSourceProgressPercent() > 10f);
            assertTrue(laterLocation.contentSourceProgressPercent() > earlyLocation.contentSourceProgressPercent() + 10f);
        }

        @Test
        void resolveCfiLocation_rangeCfiUsesRangeEndPosition() {
            String rangeCfi = "epubcfi(/6/2!/4/2,/2/1:0,/4/1:10)";

            EpubCfiService.CfiLocation location = service.resolveCfiLocation(testEpubFile, rangeCfi).orElseThrow();

            assertEquals("chapter1.xhtml", location.href());
            assertNotNull(location.contentSourceProgressPercent());
            assertTrue(location.contentSourceProgressPercent() > 30f);
        }

        @Test
        void resolveCfiLocation_invalidCfiReturnsEmpty() {
            assertTrue(service.resolveCfiLocation(testEpubFile, "invalid").isEmpty());
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
            CfiConverter converter1 = service.createConverter(testEpubFile, 0);
            CfiConverter converter2 = service.createConverter(testEpubFile, 0);

            assertNotNull(converter1);
            assertNotNull(converter2);
        }

        @Test
        void evictCache_removesEntriesForFile() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);

            service.evictCache(testEpubFile);

            CfiConverter converter = service.createConverter(testEpubFile, 0);
            assertNotNull(converter);
        }

        @Test
        void clearCache_removesAllEntries() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);

            service.clearCache();

            CfiConverter converter = service.createConverter(testEpubFile, 0);
            assertNotNull(converter);
        }

        @Test
        void cache_differentSpineIndices_cachedSeparately() {
            service.createConverter(testEpubFile, 0);
            service.createConverter(testEpubFile, 1);
            service.createConverter(testEpubFile, 2);

            CfiConverter converter0 = service.createConverter(testEpubFile, 0);
            CfiConverter converter1 = service.createConverter(testEpubFile, 1);
            CfiConverter converter2 = service.createConverter(testEpubFile, 2);

            assertNotNull(converter0);
            assertNotNull(converter1);
            assertNotNull(converter2);
        }
    }

    @Nested
    class SelfClosingElementTests {

        private static final String ANCHORED_CHAPTER = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 8</title></head>
                <body>
                    <div class="chapter" id="c8">
                        <h2><a id="chap08"/>Chapter 8</h2>
                        <p>First paragraph.</p>
                        <p>Second paragraph.</p>
                        <p>Third paragraph with <i>italic</i> text.</p>
                    </div>
                </body>
                </html>
                """;

        private File anchoredEpub;

        @BeforeEach
        void createAnchoredEpub() throws IOException {
            Book book = new Book();
            book.getMetadata().addTitle("Anchored");
            book.addSection("Chapter 8", new Resource(ANCHORED_CHAPTER.getBytes(StandardCharsets.UTF_8), "chapter8.xhtml"));
            anchoredEpub = tempDir.resolve("anchored.epub").toFile();
            try (FileOutputStream out = new FileOutputStream(anchoredEpub)) {
                new EpubWriter().write(book, out);
            }
        }

        @Test
        void convertCfiToProgressXPointer_paragraphAfterSelfClosingAnchor_pointsAtThatParagraph() {
            String webReaderCfi = "epubcfi(/6/2!/4/2[c8],/8/1:5,/8/1:20)";

            String xpointer = service.convertCfiToProgressXPointer(anchoredEpub, webReaderCfi);

            assertEquals("/body/DocFragment[1]/body/div/p[3]", xpointer);
        }

        @Test
        void convertCfiToProgressXPointer_rangeWithBodyAsCommonParent_keepsPosition() {
            String webReaderCfi = "epubcfi(/6/2!/4,/2/6/1:0,/2/8/1:4)";

            String xpointer = service.convertCfiToProgressXPointer(anchoredEpub, webReaderCfi);

            assertEquals("/body/DocFragment[1]/body/div/p[2]", xpointer);
        }

        @Test
        void convertXPointerToCfi_paragraphAfterSelfClosingAnchor_usesDocumentStructure() {
            String cfi = service.convertXPointerToCfi(anchoredEpub, "/body/DocFragment[1]/body/div/p[3]/text().5");

            assertEquals("epubcfi(/6/2!/4/2/8:5)", cfi);
        }

        @Test
        void convertCfiToProgressXPointer_html5ChapterQuotingXhtmlNamespace_keepsHtmlParsing() throws IOException {
            String html5Chapter = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><title>Parsing XHTML</title></head>
                    <body>
                        <div id="c1">
                            <p>An XHTML root: <code>&lt;html xmlns="http://www.w3.org/1999/xhtml"&gt;</code></p>
                            <hr>
                            <p>Second paragraph.</p>
                            <p>Third paragraph.</p>
                        </div>
                    </body>
                    </html>
                    """;
            Book book = new Book();
            book.getMetadata().addTitle("Parsing XHTML");
            book.addSection("Chapter 1", new Resource(html5Chapter.getBytes(StandardCharsets.UTF_8), "chapter1.html"));
            File html5Epub = tempDir.resolve("html5.epub").toFile();
            try (FileOutputStream out = new FileOutputStream(html5Epub)) {
                new EpubWriter().write(book, out);
            }

            String xpointer = service.convertCfiToProgressXPointer(html5Epub, "epubcfi(/6/2!/4/2[c1],/8/1:0,/8/1:5)");

            assertEquals("/body/DocFragment[1]/body/div/p[3]", xpointer);
        }

        @Test
        void convertCfiToProgressXPointer_xhtmlRootWithoutXmlDeclaration_handlesSelfClosingAnchor() throws IOException {
            String xhtmlChapter = """
                    <html xmlns='http://www.w3.org/1999/xhtml' lang="en">
                    <head><title>Chapter 8</title></head>
                    <body>
                        <div id="c8">
                            <h2><a id="chap08"/>Chapter 8</h2>
                            <p>First paragraph.</p>
                            <p>Second paragraph.</p>
                            <p>Third paragraph.</p>
                        </div>
                    </body>
                    </html>
                    """;
            Book book = new Book();
            book.getMetadata().addTitle("No declaration");
            book.addSection("Chapter 8", new Resource(xhtmlChapter.getBytes(StandardCharsets.UTF_8), "chapter8.xhtml"));
            File xhtmlEpub = tempDir.resolve("no-declaration.epub").toFile();
            try (FileOutputStream out = new FileOutputStream(xhtmlEpub)) {
                new EpubWriter().write(book, out);
            }

            String xpointer = service.convertCfiToProgressXPointer(xhtmlEpub, "epubcfi(/6/2!/4/2[c8],/8/1:0,/8/1:5)");

            assertEquals("/body/DocFragment[1]/body/div/p[3]", xpointer);
        }
    }

    @Nested
    class BodyRelativePathTests {

        private static final String TWO_DIVISION_CHAPTER = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Two divisions</title></head>
                <body>
                    <div id="one">
                        <p>Division one, first paragraph with enough text to move the offset along.</p>
                        <p>Division one, second paragraph with enough text to move the offset along.</p>
                    </div>
                    <div id="two">
                        <p>Division two, first paragraph with enough text to move the offset along.</p>
                        <p>Division two, second paragraph.</p>
                    </div>
                </body>
                </html>
                """;

        private File twoDivisionEpub;

        @BeforeEach
        void createTwoDivisionEpub() throws IOException {
            Book book = new Book();
            book.getMetadata().addTitle("Two divisions");
            book.addSection("Chapter", new Resource(TWO_DIVISION_CHAPTER.getBytes(StandardCharsets.UTF_8), "chapter.xhtml"));
            twoDivisionEpub = tempDir.resolve("two-divisions.epub").toFile();
            try (FileOutputStream out = new FileOutputStream(twoDivisionEpub)) {
                new EpubWriter().write(book, out);
            }
        }

        private float expectedPercentOf(String text) {
            return TWO_DIVISION_CHAPTER.indexOf(text) * 100f / TWO_DIVISION_CHAPTER.length();
        }

        @Test
        void resolveCfiLocation_rangeWithBodyAsCommonParent_resolvesSecondDivisionParagraph() {
            String rangeCfi = "epubcfi(/6/2!/4,/4/2/1:0,/4/4/1:0)";

            EpubCfiService.CfiLocation location = service.resolveCfiLocation(twoDivisionEpub, rangeCfi).orElseThrow();

            assertEquals(expectedPercentOf("Division two, second paragraph."), location.contentSourceProgressPercent(), 0.5f);
        }

        @Test
        void resolveCfiLocation_pointInSecondDivision_resolvesThatParagraph() {
            String pointCfi = "epubcfi(/6/2!/4/4/4/1:0)";

            EpubCfiService.CfiLocation location = service.resolveCfiLocation(twoDivisionEpub, pointCfi).orElseThrow();

            assertEquals(expectedPercentOf("Division two, second paragraph."), location.contentSourceProgressPercent(), 0.5f);
        }

        @Test
        void convertCfiToProgressXPointer_rangeWithBodyAsCommonParent_pointsAtRangeStart() {
            String rangeCfi = "epubcfi(/6/2!/4,/4/2/1:0,/4/4/1:0)";

            assertEquals("/body/DocFragment[1]/body/div[2]/p[1]", service.convertCfiToProgressXPointer(twoDivisionEpub, rangeCfi));
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void roundTrip_xPointerToCfiAndBack() {
            String originalXPointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = service.convertXPointerToCfi(testEpubFile, originalXPointer);
            XPointerResult result = service.convertCfiToXPointer(testEpubFile, cfi);

            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().contains("/body/DocFragment[1]/body"));
        }

        @Test
        void roundTrip_cfiToXPointerAndBack() {
            String originalCfi = "epubcfi(/6/2!/4/2/2)";

            XPointerResult xpointerResult = service.convertCfiToXPointer(testEpubFile, originalCfi);
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

            CfiConverter converter1 = service.createConverter(testEpubFile, 0);
            CfiConverter converter2 = service.createConverter(secondEpub, 0);

            assertNotNull(converter1);
            assertNotNull(converter2);
        }

        @Test
        void evictCache_onlyAffectsSpecificFile() throws IOException {
            File secondEpub = createTestEpub("second.epub");

            service.createConverter(testEpubFile, 0);
            service.createConverter(secondEpub, 0);

            service.evictCache(testEpubFile);

            CfiConverter converter = service.createConverter(secondEpub, 0);
            assertNotNull(converter);
        }
    }
}
