package org.booklore.service.kobo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.SecureXmlUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;
import org.grimmory.epub4j.domain.Book;
import org.grimmory.epub4j.domain.MediaTypes;
import org.grimmory.epub4j.domain.Resource;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.epub.EpubWriter;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KepubConversionService {
    private static final String CLASSNAME_KOBO_SPAN = "koboSpan";
    private static final String CLASSNAME_KOBO_STYLES = "kobostylehacks";
    private static final String CLASSNAME_KOBO_HYPHENATE = "kobostylehyphenate";
    private static final String ID_FORMAT_KOBO_SPAN = "kobo.%d";

    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

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

    private final Set<String> HTML_MEDIA_TYPES = Set.of(
            "text/html",
            "application/xhtml",
            "application/xhtml+xml"
    );

    private final Set<String> IGNORED_FILENAMES = Set.copyOf(
            Stream.of(
                ".DS_STORE",
                "iTunesMetadata.plist",
                "iTunesArtwork.plist",
                "calibre_bookmarks.txt",
                "thumbs.db"
            ).map(String::toLowerCase).toList()
    );

    private final Set<String> IGNORED_DIRECTORIES = Set.copyOf(
            Stream.of(
                "__MACOSX"
            ).map(String::toLowerCase).toList()
    );

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

    private final EpubReader epubReader;
    private final EpubWriter epubWriter;

    public KepubConversionService() {
        this(
                new EpubReader(),
                new EpubWriter()
        );
    }

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
                                    state.append(element);

                                    if (!state.hasPunctuation()) {
                                        return true;
                                    }

                                    // If we've seen punctuation, and this is more punctuation or other
                                    // acceptable chars, keep going.
                                    if (SENTENCE_EXTRA_CHARS.contains(element) || SENTENCE_PUNCTUATION.contains(element)) {
                                        return true;
                                    }

                                    // If end of sentence:
                                    return downstream.push(state.flush());
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
        var innerElement = document.createElement("div");
        innerElement.id("book-inner");
        innerElement.appendChildren(document.body().children());

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

    private Resource getTransformedContentResource(Resource contentResource, boolean forceEnableHyphenation) throws IOException {
        // Input can be HTML or XHTML
        // Output MUST be XHTML 1.1

        // TODO: Are we able to allow XML declarations?
        // TODO: Need to verify:
        //  NBSP must be escape with &#160;
        //  XMLNS must be on the root
        //  SVG needs XMLNS
        //  All CSS / scripts MUST have a "type" attribute
        //  All boolean attributes must have a value
        //  Void elements must be rendered self-closing
        //  Use numerical escapes instead of named escapes
        //  Comments must be XHTML standard
        //  Tables MUST have a TBODY

        try (var inputStream = contentResource.asInputStream()) {
            Document document = Jsoup.parse(inputStream, contentResource.getInputEncoding(), "/");

            transformContentAddStyles(document, forceEnableHyphenation);
            transformContentAddWrappers(document);
            transformContentAddKoboSpans(document);
            transformContentRemoveGarbage(document);

            document.outputSettings(
                    document.outputSettings()
                        .clone()
                        .charset(StandardCharsets.UTF_8)
                        .escapeMode(Entities.EscapeMode.xhtml)
                        .syntax(Document.OutputSettings.Syntax.xml)
            );

            var newResource = new Resource(
                    contentResource.getId(),
                    document.toString().getBytes(StandardCharsets.UTF_8),
                    contentResource.getHref(),
                    MediaTypes.XHTML,
                    "UTF-8"
            );

            newResource.setProperties(contentResource.getProperties());
            newResource.setMediaOverlayId(contentResource.getMediaOverlayId());

            return newResource;
        }
    }

    /**
     * Adds the cover-image property to the cover item in the OPF manifest.
     * Kobo devices will only support the EPUB3 "properties" attribute with
     * the `cover-image` tag.
     * <a href="https://www.w3.org/TR/epub-33/#sec-item-resource-properties">
     *     Read more on the EPUB3 spec.
     * </a>
     */
    private void transformOPFCoverImage(org.w3c.dom.Document opfDoc, String coverImage) {
        if (coverImage == null) {
            return;
        }

        NodeList manifestList = opfDoc.getElementsByTagNameNS(OPF_NS, "manifest");

        if (manifestList.getLength() == 0) {
            return;
        }

        if (manifestList.item(0) instanceof org.w3c.dom.Element manifest) {
            NodeList itemList = manifest.getElementsByTagNameNS(OPF_NS, "item");

            for (int i = 0; i < itemList.getLength(); i++) {
                if (itemList.item(i) instanceof org.w3c.dom.Element item) {
                    if (coverImage.equals(item.getAttribute("href"))) {
                        String properties = item.getAttribute("properties");

                        if (properties.isBlank()) {
                            properties = "cover-image";
                        } else {
                            properties += " cover-image";
                        }

                        item.setAttribute("properties", properties);
                    }
                }
            }
        }
    }

    private Resource transformOPF(Resource opfResource, Resource cover) throws IOException {
        // TransformOPF transforms the OPF document for a KEPUB.
        try {
            var builder = SecureXmlUtils.createSecureDocumentBuilder(true);
            var opfDoc = builder.parse(opfResource.asInputStream());

            String coverImage = cover == null ? null : cover.getHref();
            transformOPFCoverImage(opfDoc, coverImage);

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            try (var outputStream = new ByteArrayOutputStream()) {
                transformer.transform(new DOMSource(opfDoc), new StreamResult(outputStream));

                return new Resource(
                        opfResource.getId(),
                        outputStream.toByteArray(),
                        opfResource.getHref(),
                        opfResource.getMediaType(),
                        "UTF-8"
                );
            }

        } catch (TransformerException | SAXException | ParserConfigurationException exception) {
            log.error("unable to parse OPF");
            throw new IOException("unable to parse OPF", exception);
        }
    }

    private boolean isIncludedResource(Resource resource) {
        // Because this isn't an actual filesystem it's always "/"
        String[] parts = resource.getHref().split("/");

        if (parts.length == 0) {
            // No empty HREF items allowed.
            return false;
        }

        if (IGNORED_FILENAMES.contains(parts[parts.length - 1].toLowerCase())) {
            return false;
        }

        // Check everything except the "filename" (the last entry)
        for (int i = 0; i < parts.length - 1; i++) {
            if (IGNORED_DIRECTORIES.contains(parts[i].toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private String getMediaType(Resource resource) {
        if (resource == null || resource.getMediaType() == null) {
            return null;
        }

        return resource.getMediaType().toString().toLowerCase();
    }

    private Book convertBookToKepub(Book original, boolean forceEnableHyphenation) throws IOException {
        Book kepub = new Book();

        for (var resource : original.getResources().getAll()) {
            if (!isIncludedResource(resource)) {
                continue;
            }

            var resourceMediaType = getMediaType(resource);

            if (resourceMediaType != null && HTML_MEDIA_TYPES.contains(resourceMediaType)) {
                kepub.addResource(getTransformedContentResource(resource, forceEnableHyphenation));
            } else {
                kepub.addResource(resource);
            }
        }

        kepub.setNavResource(original.getNavResource());
        kepub.setNcxResource(original.getNcxResource());
        kepub.setCoverImage(original.getCoverImage());
        kepub.setCoverPage(original.getCoverPage());
        kepub.setMetadata(original.getMetadata());
        kepub.setTableOfContents(original.getTableOfContents());
        kepub.setSpine(original.getSpine());

        kepub.setOpfResource(
                transformOPF(
                        original.getOpfResource(),
                        original.getCoverImage()
                )
        );

        return kepub;
    }

    public void convertEpubToKepub(InputStream inputStream, OutputStream outputStream, boolean forceEnableHyphenation) throws IOException {
        Book originalBook = epubReader.readEpub(inputStream);

        Book kepubBook = convertBookToKepub(originalBook, forceEnableHyphenation);

        epubWriter.write(kepubBook, outputStream);
    }

    private void convertEpubToKepub(Path inputPath, Path outputPath, boolean forceEnableHyphenation) throws IOException {
        validateInputs(inputPath);

        try (var inputStream = Files.newInputStream(inputPath)) {
            try (var outputStream = Files.newOutputStream(outputPath)) {
                convertEpubToKepub(inputStream, outputStream, forceEnableHyphenation);
            }
        }

        log.info(
                "Successfully converted {} to {} (size: {} bytes)",
                inputPath.getFileName(),
                outputPath.getFileName(),
                Files.size(outputPath)
        );
    }

    public File convertEpubToKepub(File epubFile, File tempDir, boolean forceEnableHyphenation) throws IOException {
        var outputPath = Files.createTempFile(tempDir.getPath(), ".kepub.epub");
        convertEpubToKepub(epubFile.toPath(), outputPath, forceEnableHyphenation);
        return outputPath.toFile();
    }

    private void validateInputs(Path inputPath) {
        if (inputPath == null || !Files.isRegularFile(inputPath) || !inputPath.endsWith(".epub")) {
            throw new IllegalArgumentException("Invalid EPUB file: " + inputPath);
        }
    }
}
