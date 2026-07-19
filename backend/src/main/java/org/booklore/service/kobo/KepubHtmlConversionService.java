package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;
import javax.xml.transform.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KepubHtmlConversionService {
    private static final String CLASSNAME_KOBO_SPAN = "koboSpan";
    private static final String CLASSNAME_KOBO_STYLES = "kobostylehacks";
    private static final String CLASSNAME_KOBO_HYPHENATE = "kobostylehyphenate";
    private static final String ID_FORMAT_KOBO_SPAN = "kobo.%d";

    private static final String CSS_KOBO_STYLES = """
            div#book-inner {
                margin-top: 0;
                margin-bottom: 0;
            }
            """;

    private static final String CSS_HYPHENATE = """
            * {
                -webkit-hyphens: auto;
                -moz-hyphens: auto;
                hyphens: auto;
        
                -webkit-hyphenate-limit-after: 3;
                -webkit-hyphenate-limit-before: 3;
                -webkit-hyphenate-limit-lines: 2;
            }
        
            h1, h2, h3, h4, h5, h6, td {
                -moz-hyphens: none !important;
                -webkit-hyphens: none !important;
                hyphens: none !important;
            }
            """;

    private static final String CSS_NO_HYPHENATE = """
            * {
                -moz-hyphens: none !important;
                -webkit-hyphens: none !important;
                hyphens: none !important;
            }
            """;

    private final Set<Integer> SENTENCE_PUNCTUATION = Set.of(
            (int) '.',
            (int) '?',
            (int) '!',
            (int) '…'
    );

    private final Set<Integer> SENTENCE_EXTRA_CHARS = Set.of(
            (int) '\'',
            (int) '"',
            (int) '“',
            (int) '”',
            (int) '’'
    );

    private class SentenceParsingState {
        private final StringBuilder window = new StringBuilder();
        private boolean hasSeenPunctuation = false;

        public void append(int codePoint) {
            window.appendCodePoint(codePoint);
            if (SENTENCE_PUNCTUATION.contains(codePoint)) {
                hasSeenPunctuation = true;
            }
        }

        public boolean isEmpty() {
            return window.isEmpty();
        }

        public boolean hasPunctuation() {
            return hasSeenPunctuation;
        }

        public void reset() {
            window.setLength(0);
            hasSeenPunctuation = false;
        }

        public String flush() {
            String value = window.toString();
            this.reset();
            return value;
        }
    }

    private Stream<String> getSentences(String text) {
        return text.codePoints()
                .boxed()
                .gather(
                        Gatherer.ofSequential(
                                SentenceParsingState::new,
                                Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
                                    if (!state.hasPunctuation()) {
                                        state.append(element);
                                        return true;
                                    }

                                    // If we've seen punctuation, and this is more punctuation or other
                                    // acceptable chars, keep going.
                                    if (SENTENCE_EXTRA_CHARS.contains(element) || SENTENCE_PUNCTUATION.contains(element)) {
                                        state.append(element);
                                        return true;
                                    }

                                    // If end of sentence:
                                    var result = downstream.push(state.flush());
                                    state.append(element);
                                    return result;
                                }),
                                (state, downstream) -> {
                                    if (!state.isEmpty() && !downstream.isRejecting()) {
                                        downstream.push(state.flush());
                                    }
                                }
                        )
                );
    }

    /**
     * Find every image and text node, and add `kobospan` elements where
     * appropriate - around each image, and around each sentence in the
     * text node.
     */
    private void transformContentAddKoboSpans(Document document) {
        // Iterate through all elements with text & split to sentences.

        // Wrap sentences in <span class="koboSpan" id="kobo.1"></span>
        // Also wrap each image
        AtomicInteger koboSpanIndex = new AtomicInteger();

        var nodeIterator = document.body().nodeStream().iterator();
        while (nodeIterator.hasNext()) {
            var node = nodeIterator.next();

            var parent = node.parentElement();

            if (parent == null) {
                // Node is not in the DOM or does not have parent.
                // Cannot operate on it.
                continue;
            }

            if ("span".equals(parent.tagName()) && parent.hasClass(CLASSNAME_KOBO_SPAN)) {
                // The iterator will pick up the koboSpan we're adding
                continue;
            }

            if (node instanceof TextNode textNode) {
                if (textNode.isBlank()) {
                    continue;
                }

                var koboSpans = getSentences(textNode.text())
                        .map(sentence -> {
                            var koboSpan = document.createElement("span");
                            koboSpan.id(String.format(ID_FORMAT_KOBO_SPAN, koboSpanIndex.incrementAndGet()));
                            koboSpan.addClass(CLASSNAME_KOBO_SPAN);
                            koboSpan.text(sentence);
                            return koboSpan;
                        })
                        .toList();

                for (var span : koboSpans) {
                    textNode.before(span);
                }

                textNode.remove();
            }


            if (node instanceof Element element) {
                if ("img".equals(element.tagName()) || "svg".equals(element.tagName())) {
                    var koboSpan = document.createElement("span");
                    koboSpan.id("kobo." + koboSpanIndex.incrementAndGet());
                    koboSpan.addClass(CLASSNAME_KOBO_SPAN);

                    element.before(koboSpan);
                    koboSpan.appendChild(element);
                }
            }
        }
    }

    private void transformContentAddStyles(Document document, boolean forceEnableHyphenation) {
        document.head().getElementsByClass(CLASSNAME_KOBO_STYLES).remove();
        document.head().getElementsByClass(CLASSNAME_KOBO_HYPHENATE).remove();

        document.head().appendChild(
                document.createElement("style")
                        .addClass(CLASSNAME_KOBO_STYLES)
                        .attr("type", "text/css")
                        .text(CSS_KOBO_STYLES)
        );

        if (forceEnableHyphenation) {
            document.head().appendChild(
                    document.createElement("style")
                            .addClass(CLASSNAME_KOBO_HYPHENATE)
                            .attr("type", "text/css")
                            .text(CSS_HYPHENATE)
            );
        } else {
            document.head().appendChild(
                    document.createElement("style")
                            .addClass(CLASSNAME_KOBO_HYPHENATE)
                            .attr("type", "text/css")
                            .text(CSS_NO_HYPHENATE)
            );
        }
    }

    /**
     * Wraps the `body` of the document in two divs:
     * body > div#book-columns > div#book-inner > *
     */
    private void transformContentAddWrappers(Document document) {
        var children = document.body().childNodes();

        var innerElement = document.createElement("div");
        innerElement.id("book-inner");
        innerElement.appendChildren(children);

        var columnElement = document.createElement("div");
        columnElement.id("book-columns");
        columnElement.appendChild(innerElement);

        document.body().appendChild(columnElement);
    }

    private void transformContentRemoveGarbage(Document document) {
        // Adobe Adept elements
        var adobeAdeptExpectedResources = document.getElementsByAttributeValue("name", "Adept.expected.resource");
        for (var element : adobeAdeptExpectedResources) {
            element.remove();
        }

        // More adobe Adept elements
        var adobeAdeptResources = document.getElementsByAttributeValue("name", "Adept.resource");
        for (var element : adobeAdeptResources) {
            element.remove();
        }

        // TODO: Remove content:
        // Invalid UTF-8 characters (�)
        // Empty MSWord o:p // st1:* tags
    }

    private void transformContentAddXmlns(Document document) {
        // Add XHTML XMLNS
        document.getElementsByTag("html")
                .attr("xmlns", "http://www.w3.org/1999/xhtml");

        // Add SVG XMLNS
        document.getElementsByTag("svg")
                .attr("xmlns", "http://www.w3.org/2000/svg");
    }

    private void transformDocument(Document document, boolean forceEnableHyphenation) {
        document.outputSettings(
                document.outputSettings()
                        .clone()
                        .escapeMode(Entities.EscapeMode.xhtml)
                        .syntax(Document.OutputSettings.Syntax.xml)
        );

        document.charset(StandardCharsets.UTF_8);

        transformContentAddStyles(document, forceEnableHyphenation);
        transformContentAddWrappers(document);
        transformContentAddKoboSpans(document);
        transformContentRemoveGarbage(document);
        transformContentAddXmlns(document);
    }

    public String transform(String html, boolean forceEnableHyphenation) {
        Document document = Jsoup.parse(html, "/");
        transformDocument(document, forceEnableHyphenation);
        return document.toString();
    }

    public String transform(InputStream stream, String inputEncoding, boolean forceEnableHyphenation) throws IOException {
        Document document = Jsoup.parse(stream, inputEncoding, "/");
        transformDocument(document, forceEnableHyphenation);
        return document.toString();
    }
}
