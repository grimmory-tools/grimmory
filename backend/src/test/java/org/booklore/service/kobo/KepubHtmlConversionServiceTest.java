package org.booklore.service.kobo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Pattern;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class KepubHtmlConversionServiceTest {
    @InjectMocks
    private KepubHtmlConversionService service;

    @Test
    void transform_ShouldSplitSentences() {
        String actual = service.transform("<html><body><p>Hello.World.  This is a test!</p></body></html>", false);

        assertThat(actual).contains(
                "<span id=\"kobo.1.1\" class=\"koboSpan\">Hello.</span>"
        );
        assertThat(actual).contains(
                "<span id=\"kobo.1.2\" class=\"koboSpan\">World.</span>"
        );
        assertThat(actual).contains(
                "<span id=\"kobo.1.3\" class=\"koboSpan\"> This is a test!</span>"
        );
    }

    @Test
    void transform_shouldIndexElementsSeparateFromSentences() {
        String actual = service.transform(
                "<html><body><p>What a guy.  Makes you cry.</p><p>And I did.</p></html>",
                false
        );

        assertThat(actual).contains("<span id=\"kobo.1.1\" class=\"koboSpan\">What a guy.</span>");
        assertThat(actual).contains("<span id=\"kobo.1.2\" class=\"koboSpan\"> Makes you cry.</span>");
        assertThat(actual).contains("<span id=\"kobo.2.1\" class=\"koboSpan\">And I did.</span>");
    }

    @Test
    void transform_ShouldWrapImages() {
        String actual = service.transform("<html><body><p>Hello World.<img /></p></body></html>", false);

        assertThat(actual).contains(
                "<span id=\"kobo.2.1\" class=\"koboSpan\"><img /></span>"
        );
    }

    @Test
    void transform_ShouldWrapMultipleImages() {
        String actual = service.transform("<html><body><p>Hello World.<img /><img /></p></body></html>", false);

        assertThat(actual).contains(
                "<span id=\"kobo.2.1\" class=\"koboSpan\"><img /></span>"
        );
        assertThat(actual).contains(
                "<span id=\"kobo.3.1\" class=\"koboSpan\"><img /></span>"
        );
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

        assertThat(actual).contains("<span id=\"kobo.1.1\" class=\"koboSpan\">Already marked.</span>");
        assertThat(actual).contains("<span id=\"kobo.1.2\" class=\"koboSpan\"> New text.</span>");
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

    private int countKoboSpans(String html) {
        return countOccurrences(html, "class=\"koboSpan\"");
    }

    private int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }
}