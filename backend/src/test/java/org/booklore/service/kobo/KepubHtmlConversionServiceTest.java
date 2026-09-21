package org.booklore.service.kobo;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class KepubHtmlConversionServiceTest {
    @InjectMocks
    private KepubHtmlConversionService service;

    @Test
    void transform_ShouldSplitSentences() {
        String actual = service.transform("<html><body><p>Hello.World.  This is a test!</p></body></html>", false);

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.1.1")).isEqualTo("Hello.");
        assertThat(koboSpans.get("kobo.1.2")).isEqualTo("World.");
        assertThat(koboSpans.get("kobo.1.3")).isEqualTo("This is a test!");
    }

    @Test
    void transform_shouldIndexElementsSeparateFromSentences() {
        String actual = service.transform(
                "<html><body><p>What a guy.  Makes you cry.</p><p>And I did.</p></html>",
                false
        );

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.1.1")).isEqualTo("What a guy.");
        assertThat(koboSpans.get("kobo.1.2")).isEqualTo("Makes you cry.");
        assertThat(koboSpans.get("kobo.2.1")).isEqualTo("And I did.");
    }

    @Test
    void transform_ShouldWrapImages() {
        String actual = service.transform("<html><body><p>Hello World.<img /></p></body></html>", false);

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.2.1")).isEqualTo("<img>");
    }

    @Test
    void transform_ShouldWrapMultipleImages() {
        String actual = service.transform("<html><body><p>Hello World.<img /><img /></p></body></html>", false);

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.2.1")).isEqualTo("<img>");
        assertThat(koboSpans.get("kobo.3.1")).isEqualTo("<img>");
    }

    @Test
    void transform_ShouldIncludeCSSHacks() {
        String actual = service.transform("<html><body><p>Hello World.</p></body></html>", false);

        assertThat(actual).contains("class=\"kobostylehacks\"");
    }

    @Test
    void transform_ShouldIncludeRootXmlns() {
        String actual = service.transform("<html><body><p>Hello World.</p></body></html>", false);

        assertThat(actual).contains("xmlns=\"http://www.w3.org/1999/xhtml\"");
    }

    @Test
    void transform_ShouldIncludeSVGXmlns() {
        String actual = service.transform("<html><body><svg></svg></body></html>", false);

        assertThat(actual).contains("<svg xmlns=\"http://www.w3.org/2000/svg\"");
    }

    @Test
    void transform_ShouldNotWrapSVGChildren() {
        String actual = service.transform("<html><body><svg><text>Example</text></svg></body></html>", false);

        assertThat(actual).matches(
                Pattern.compile(
                        ".*<svg[^<>]+>\\s*<text>\\s*Example\\s*</text>.*",
                        Pattern.MULTILINE | Pattern.DOTALL
                )
        );
    }

    @Test
    void transform_shouldWrapBody() {
        String actual = service.transform(
                "<html><body><p>Hello World.</p></body></html>",
                false
        );

        assertThat(actual).matches(
                Pattern.compile(
                        ".*<body>\\s*<div id=\"book-columns\">\\s*<div id=\"book-inner\">.*",
                        Pattern.DOTALL | Pattern.MULTILINE
                )
        );
    }

    @Test
    void transform_ShouldRemoveAdobeAdept() {
        String actual = service.transform(
                "<html><body><span name=\"Adept.expected.resource\">Remove</span>" +
                        "<span name=\"Adept.expected.resource\">Remove</span></body></html>",
                false
        );

        assertThat(actual).doesNotContain("Remove");
    }

    @Test
    void transform_ShouldHandleEmptyDocuments() {
        String actual = service.transform(
                "<html><body></body></html>",
                false
        );

        assertThat(actual).doesNotContain("Remove");
    }

    @Test
    void transform_ShouldMarkEveryTextNodeInAnIllustratedDocument() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            body.append("<div><img src=\"picture.jpg\" /><span>Entry ").append(i).append(".</span></div>");
        }

        String actual = service.transform("<html><body>" + body + "</body></html>", false);

        assertThat(countKoboSpans(actual)).isEqualTo(40);
    }

    @Test
    void transform_ShouldNotMarkInsideMathML() {
        String actual = service.transform(
                "<html><body><p>See <math xmlns=\"http://www.w3.org/1998/Math/MathML\">" +
                        "<mi>x</mi><mo>+</mo><mn>1</mn></math> here.</p></body></html>",
                false
        );

        assertThat(actual).doesNotMatch(Pattern.compile(".*<m[iong][^>]*>\\s*<span[^>]*koboSpan.*", Pattern.DOTALL));
    }

    @Test
    void transform_ShouldNotReuseAnExistingMarkerId() {
        String actual = service.transform(
                "<html><body><p><span id=\"kobo.1.1\" class=\"koboSpan\">Already marked.</span>" +
                        " New text.</p></body></html>",
                false
        );

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.1.1")).isEqualTo("Already marked.");
        assertThat(koboSpans.get("kobo.1.2")).isEqualTo("New text.");
    }

    @Test
    void transform_ShouldPreserveWhitespaceInPreformattedText() {
        String actual = service.transform(
                "<html><body><pre>alpha  beta\n  gamma</pre></body></html>",
                false
        );

        assertThat(actual).contains("alpha  beta\n  gamma");
    }

    @Test
    void transform_ShouldAllowSelfClosingAnchor() {
        String actual = service.transform(
                "<html><body><p><a id=\"pagebreak\"/>Body text.</p></body></html>",
                false
        );

        assertThat(actual).contains("<a id=\"pagebreak\" />");
    }

    @Test
    void transform_ShouldAllowSelfClosingSpan() {
        String actual = service.transform(
                "<html><body><span id=\"pagebreak\"/>Body text.</body></html>",
                false
        );

        assertThat(actual).contains("<span id=\"pagebreak\" />");
    }

    @Test
    void transform_shouldRetainNonBreakingSpaces() {
        String actual = service.transform(
                "<html><body>Body&nbsp;Text</html>",
                false
        );

        assertThat(actual).contains("Body&#xa0;Text");
    }

    @Test
    void transform_incrementsForSentenceAndParagraphAsExpected() {
        String actual = service.transform(
                """
                <?xml version='1.0' encoding='utf-8'?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                First <h2>Second</h2>
                <div>Third
                <ol><li>Fourth</li>
                <li><p>Fifth</p></li>
                </ol>
                Sixth
                </div>
                </body>
                </html>
                """,
                false
        );

        var koboSpans = getKoboSpans(actual);

        assertThat(koboSpans.get("kobo.0.1")).isEqualTo("First");
        assertThat(koboSpans.get("kobo.1.1")).isEqualTo("Second");
        assertThat(koboSpans.get("kobo.1.2")).isEqualTo("Third");
        assertThat(koboSpans.get("kobo.2.1")).isEqualTo("Fourth");
        assertThat(koboSpans.get("kobo.3.1")).isEqualTo("Fifth");
        assertThat(koboSpans.get("kobo.3.2")).isEqualTo("Sixth");
    }

    private Map<String, String> getKoboSpans(String html) {
        var koboSpans = new HashMap<String, String>();
        var doc = Jsoup.parse(html);
        for (var element : doc.getElementsByClass("koboSpan")) {
            koboSpans.put(element.attr("id"), element.html().trim());
        }
        return koboSpans;
    }

    private int countKoboSpans(String html) {
        return countOccurrences(html, "class=\"koboSpan\"");
    }

    private int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }
}