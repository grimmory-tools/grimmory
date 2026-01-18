package com.adityachandel.booklore.util.koreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CfiConvertorTest {

    private Document simpleDocument;
    private Document complexDocument;

    @BeforeEach
    void setUp() {
        // Simple HTML document for basic tests
        simpleDocument = Jsoup.parse("""
                <!DOCTYPE html>
                <html>
                <body>
                    <div id="chapter1">
                        <p>First paragraph with some text.</p>
                        <p>Second paragraph with more text.</p>
                    </div>
                    <div id="chapter2">
                        <p>Third paragraph.</p>
                    </div>
                </body>
                </html>
                """);

        // Complex HTML document with nested elements
        complexDocument = Jsoup.parse("""
                <!DOCTYPE html>
                <html>
                <body>
                    <section>
                        <div class="content">
                            <h1>Chapter Title</h1>
                            <p>This is <em>emphasized</em> and <strong>bold</strong> text.</p>
                            <p>Another paragraph with <span class="highlight">highlighted</span> content.</p>
                        </div>
                        <div class="sidebar">
                            <p>Sidebar content here.</p>
                        </div>
                    </section>
                    <section>
                        <div>
                            <p>Second section paragraph.</p>
                        </div>
                    </section>
                </body>
                </html>
                """);
    }

    @Nested
    class ExtractSpineIndexTests {

        @Test
        void extractSpineIndex_fromCfi_spineIndex0() {
            String cfi = "epubcfi(/6/2!/4/2)";
            int result = CfiConvertor.extractSpineIndex(cfi);
            assertEquals(0, result);
        }

        @Test
        void extractSpineIndex_fromCfi_spineIndex1() {
            String cfi = "epubcfi(/6/4!/4/2)";
            int result = CfiConvertor.extractSpineIndex(cfi);
            assertEquals(1, result);
        }

        @Test
        void extractSpineIndex_fromCfi_spineIndex5() {
            String cfi = "epubcfi(/6/12!/4/2:10)";
            int result = CfiConvertor.extractSpineIndex(cfi);
            assertEquals(5, result);
        }

        @Test
        void extractSpineIndex_fromXPointer_spineIndex0() {
            String xpointer = "/body/DocFragment[1]/body/div/p";
            int result = CfiConvertor.extractSpineIndex(xpointer);
            assertEquals(0, result);
        }

        @Test
        void extractSpineIndex_fromXPointer_spineIndex2() {
            String xpointer = "/body/DocFragment[3]/body/section/div/p";
            int result = CfiConvertor.extractSpineIndex(xpointer);
            assertEquals(2, result);
        }

        @Test
        void extractSpineIndex_fromXPointer_withTextOffset() {
            String xpointer = "/body/DocFragment[5]/body/p/text().42";
            int result = CfiConvertor.extractSpineIndex(xpointer);
            assertEquals(4, result);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        void extractSpineIndex_nullOrEmpty_throwsException(String input) {
            assertThrows(IllegalArgumentException.class,
                    () -> CfiConvertor.extractSpineIndex(input));
        }

        @Test
        void extractSpineIndex_invalidFormat_throwsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> CfiConvertor.extractSpineIndex("invalid/path/here"));
        }

        @Test
        void extractSpineIndex_malformedCfi_throwsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> CfiConvertor.extractSpineIndex("epubcfi(malformed)"));
        }
    }

    @Nested
    class XPointerToCfiTests {

        @Test
        void xPointerToCfi_simpleElement() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
            assertTrue(cfi.endsWith(")"));
            assertTrue(cfi.contains("/6/2!")); // Spine index 0 = step 2
        }

        @Test
        void xPointerToCfi_withTextOffset() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().5";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
            assertTrue(cfi.contains(":5")); // Text offset
        }

        @Test
        void xPointerToCfi_differentSpineIndex() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 2);
            String xpointer = "/body/DocFragment[3]/body/div[1]/p[1]";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.contains("/6/6!")); // Spine index 2 = step 6
        }

        @Test
        void xPointerToCfi_bodyOnly() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String xpointer = "/body/DocFragment[1]/body";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
        }

        @Test
        void xPointerToCfi_rangeWithStartAndEnd() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String startXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().0";
            String endXPointer = "/body/DocFragment[1]/body/div[1]/p[1]/text().10";

            String cfi = converter.xPointerToCfi(startXPointer, endXPointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
        }

        @Test
        void xPointerToCfi_invalidPath_throwsException() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> converter.xPointerToCfi("/invalid/path"));
        }
    }

    @Nested
    class CfiToXPointerTests {

        @Test
        void cfiToXPointer_simpleElement() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult result = converter.cfiToXPointer(cfi);

            assertNotNull(result);
            assertNotNull(result.getXpointer());
            assertTrue(result.getXpointer().startsWith("/body/DocFragment[1]/body"));
        }

        @Test
        void cfiToXPointer_withTextOffset() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/2!/4/2/2:5)";

            CfiConvertor.XPointerResult result = converter.cfiToXPointer(cfi);

            assertNotNull(result);
            assertTrue(result.getXpointer().contains("/text()."));
        }

        @Test
        void cfiToXPointer_spineIndexMismatch_throwsException() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/4!/4/2)"; // Spine index 1, but converter is 0

            assertThrows(IllegalArgumentException.class,
                    () -> converter.cfiToXPointer(cfi));
        }

        @Test
        void cfiToXPointer_invalidCfiFormat_throwsException() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> converter.cfiToXPointer("not-a-valid-cfi"));
        }

        @Test
        void cfiToXPointer_malformedCfi_throwsException() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);

            assertThrows(IllegalArgumentException.class,
                    () -> converter.cfiToXPointer("epubcfi(invalid)"));
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void validateCfi_validCfi_returnsTrue() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/2!/4/2)";

            assertTrue(converter.validateCfi(cfi));
        }

        @Test
        void validateCfi_invalidCfi_returnsFalse() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);

            assertFalse(converter.validateCfi("invalid"));
            assertFalse(converter.validateCfi("epubcfi(malformed)"));
        }

        @Test
        void validateCfi_wrongSpineIndex_returnsFalse() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/10!/4/2)"; // Spine index 4, converter is 0

            assertFalse(converter.validateCfi(cfi));
        }

        @Test
        void validateXPointer_validXPointer_returnsTrue() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            assertTrue(converter.validateXPointer(xpointer));
        }

        @Test
        void validateXPointer_invalidXPointer_returnsFalse() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);

            assertFalse(converter.validateXPointer("/invalid/path"));
            assertFalse(converter.validateXPointer("not-an-xpointer"));
        }

        @Test
        void validateXPointer_withPos1_returnsTrue() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String pos0 = "/body/DocFragment[1]/body/div[1]/p[1]/text().0";
            String pos1 = "/body/DocFragment[1]/body/div[1]/p[1]/text().10";

            assertTrue(converter.validateXPointer(pos0, pos1));
        }
    }

    @Nested
    class NormalizeProgressXPointerTests {

        @Test
        void normalizeProgressXPointer_removesTextOffset() {
            String xpointer = "/body/DocFragment[1]/body/div/p/text().42";

            String result = CfiConvertor.normalizeProgressXPointer(xpointer);

            assertEquals("/body/DocFragment[1]/body/div/p", result);
        }

        @Test
        void normalizeProgressXPointer_removesNodeSuffix() {
            String xpointer = "/body/DocFragment[1]/body/div/p.5";

            String result = CfiConvertor.normalizeProgressXPointer(xpointer);

            assertEquals("/body/DocFragment[1]/body/div/p", result);
        }

        @Test
        void normalizeProgressXPointer_removesBothTextOffsetAndNodeSuffix() {
            String xpointer = "/body/DocFragment[1]/body/div/p/text().10.5";

            String result = CfiConvertor.normalizeProgressXPointer(xpointer);

            assertEquals("/body/DocFragment[1]/body/div/p", result);
        }

        @Test
        void normalizeProgressXPointer_noChangesNeeded() {
            String xpointer = "/body/DocFragment[1]/body/div/p";

            String result = CfiConvertor.normalizeProgressXPointer(xpointer);

            assertEquals("/body/DocFragment[1]/body/div/p", result);
        }

        @Test
        void normalizeProgressXPointer_nullInput_returnsNull() {
            assertNull(CfiConvertor.normalizeProgressXPointer(null));
        }

        @ParameterizedTest
        @CsvSource({
                "/body/DocFragment[1]/body/text().0, /body/DocFragment[1]/body",
                "/body/DocFragment[3]/body/section/div/p/text().100, /body/DocFragment[3]/body/section/div/p",
                "/body/DocFragment[1]/body/div[2]/p[3]/text().5, /body/DocFragment[1]/body/div[2]/p[3]"
        })
        void normalizeProgressXPointer_variousInputs(String input, String expected) {
            assertEquals(expected, CfiConvertor.normalizeProgressXPointer(input));
        }
    }

    @Nested
    class ComplexDocumentTests {

        @Test
        void xPointerToCfi_nestedElements() {
            CfiConvertor converter = new CfiConvertor(complexDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/section[1]/div[1]/p[1]";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
        }

        @Test
        void xPointerToCfi_deeplyNestedElement() {
            CfiConvertor converter = new CfiConvertor(complexDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/section[1]/div[1]/h1";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
        }

        @Test
        void xPointerToCfi_secondSection() {
            CfiConvertor converter = new CfiConvertor(complexDocument, 0);
            String xpointer = "/body/DocFragment[1]/body/section[2]/div/p";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
        }
    }

    @Nested
    class RoundTripTests {

        @Test
        void roundTrip_xPointerToCfiAndBack() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String originalXPointer = "/body/DocFragment[1]/body/div[1]/p[1]";

            String cfi = converter.xPointerToCfi(originalXPointer);
            CfiConvertor.XPointerResult result = converter.cfiToXPointer(cfi);

            assertNotNull(result.getXpointer());
            // The path should resolve to the same element, though format may differ
            assertTrue(result.getXpointer().contains("/body/DocFragment[1]/body"));
        }

        @Test
        void roundTrip_cfiToXPointerAndBack() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String originalCfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult xpointerResult = converter.cfiToXPointer(originalCfi);
            String cfi = converter.xPointerToCfi(xpointerResult.getXpointer());

            assertNotNull(cfi);
            assertTrue(cfi.startsWith("epubcfi("));
            assertTrue(cfi.contains("/6/2!")); // Same spine index
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void constructor_withDefaultSpineIndex() {
            CfiConvertor converter = new CfiConvertor(simpleDocument);

            // Should work with spine index 0
            String xpointer = "/body/DocFragment[1]/body/div[1]/p[1]";
            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.contains("/6/2!")); // Default spine index 0
        }

        @Test
        void xPointerToCfi_elementWithoutIndex() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            // Using tag without index (implicit [1])
            String xpointer = "/body/DocFragment[1]/body/div/p";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
        }

        @Test
        void xPointerToCfi_largeSpineIndex() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 99);
            String xpointer = "/body/DocFragment[100]/body/div[1]/p[1]";

            String cfi = converter.xPointerToCfi(xpointer);

            assertNotNull(cfi);
            assertTrue(cfi.contains("/6/200!")); // Spine index 99 = step 200
        }

        @Test
        void xPointerResult_allFieldsPopulated() {
            CfiConvertor converter = new CfiConvertor(simpleDocument, 0);
            String cfi = "epubcfi(/6/2!/4/2/2)";

            CfiConvertor.XPointerResult result = converter.cfiToXPointer(cfi);

            assertNotNull(result.getXpointer());
            assertNotNull(result.getPos0());
            assertNotNull(result.getPos1());
        }
    }
}
