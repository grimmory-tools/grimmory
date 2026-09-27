package org.booklore.util.koreader;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexedXPointerTest {

    private CfiConverter converter(String html) {
        return new CfiConverter(new JsoupDocumentNavigator(Jsoup.parse(html)), 0);
    }

    @ParameterizedTest
    @CsvSource({"1, 0, 1:0", "1, 3, 1:3", "2, 2, 3:2", "3, 4, 5:4"})
    void selectsDirectTextNodeAndPreservesOffset(int index, int offset, String terminal) {
        CfiConverter converter = converter("<p>Before <em>italic</em> between <b>bold</b> after</p>");

        assertEquals("epubcfi(/6/2!/4/2/" + terminal + ")",
                converter.xPointerToCfi("/body/DocFragment[1]/body/p[1]/text()[" + index + "]." + offset));
    }

    @Test
    void resolvesNestedPathWithinItsParentInsteadOfGlobally() {
        CfiConverter converter = converter("<div><p><i>Earlier</i></p></div><div><p><i>Target text</i></p></div>");

        assertEquals("epubcfi(/6/2!/4/4/2/2/1:3)",
                converter.xPointerToCfi("/body/DocFragment[1]/body/div[2]/p[1]/i[1]/text()[1].3"));
    }

    @Test
    void countsElementsWithoutTextWhenComputingCfiStep() {
        CfiConverter converter = converter("<p><a id='anchor'></a><img src='image.png'>Target</p>");

        assertEquals("epubcfi(/6/2!/4/2/5:2)",
                converter.xPointerToCfi("/body/DocFragment[1]/body/p[1]/text()[1].2"));
    }

    @Test
    void preservesWhitespaceAndConvertsCodePointsToUtf16() {
        CfiConverter converter = converter("<p>  A😀B</p>");

        assertEquals("epubcfi(/6/2!/4/2/1:5)",
                converter.xPointerToCfi("/body/DocFragment[1]/body/p[1]/text()[1].4"));
    }

    @Test
    void combinesTextSeparatedByCommentsForCfiOffset() {
        CfiConverter converter = converter("<p>Before<!-- note -->after</p>");

        assertEquals("epubcfi(/6/2!/4/2/1:8)",
                converter.xPointerToCfi("/body/DocFragment[1]/body/p[1]/text()[2].2"));
    }

    @Test
    void preservesLegacyUnindexedTextConversion() {
        CfiConverter converter = converter("<div><p>First</p></div><div><p>Second</p></div>");

        assertEquals("epubcfi(/6/2!/4/4/2:3)",
                converter.xPointerToCfi("/body/DocFragment[1]/body/p[2]/text().3"));
    }

    @Test
    void convertsIndexedTextCfiBackWithoutDescendingIntoInlineElement() {
        CfiConverter converter = converter("<p>Before <em>italic</em> after</p>");

        // EpubCfiService removes the leading /4 body step before conversion.
        assertEquals("/body/DocFragment[1]/body/p/text()[2].3",
                converter.cfiToXPointer("epubcfi(/6/2!/2/3:3)").getXpointer());
    }

    @Test
    void convertsUtf16CfiOffsetBackToCodePoints() {
        CfiConverter converter = converter("<p>A😀B</p>");

        assertEquals("/body/DocFragment[1]/body/p/text()[1].2",
                converter.cfiToXPointer("epubcfi(/6/2!/2/1:3)").getXpointer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text()[0].0", "text()[-1].0", "text()[2].0", "text()[1].-1",
            "text()[1].99", "text()[x].0", "text()[1].x", "text()[2147483648].0"})
    void rejectsInvalidTextPositions(String terminal) {
        CfiConverter converter = converter("<p>Text</p>");

        assertThrows(IllegalArgumentException.class,
                () -> converter.xPointerToCfi("/body/DocFragment[1]/body/p[1]/" + terminal));
    }

    @ParameterizedTest
    @CsvSource({"text().42", "text()[1].42", "text()[2].0"})
    void normalizesIndexedAndUnindexedProgress(String terminal) {
        assertEquals("/body/DocFragment[1]/body/p[1]",
                CfiConverter.normalizeProgressXPointer("/body/DocFragment[1]/body/p[1]/" + terminal));
    }
}
