package org.booklore.util.koreader;

import org.booklore.exception.ApiError;
import org.booklore.util.epub.EpubContentReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class EpubCfiService {

    // Drops the leading body step (/4, optionally with an id assertion) after the indirection,
    // including range CFIs whose common parent is the body itself (e.g. "!/4,/2/1:0,/6/1:8").
    private static final Pattern CFI_CLEANER_PATTERN = Pattern.compile("!/4(?:\\[[^\\]]*\\])?(?=[/,)])");
    private static final Pattern XML_DECLARATION_PATTERN = Pattern.compile("^\\uFEFF?\\s*<\\?xml\\b");
    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";
    private final Cache<String, Document> documentCache;

    public EpubCfiService() {
        this.documentCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
    }

    public XPointerResult convertCfiToXPointer(File epubFile, String cfi) {
        String normalizedCfi = normalizeContentDocumentCfi(cfi);
        int spineIndex = CfiConverter.extractSpineIndex(normalizedCfi);
        CfiConverter converter = createConverter(epubFile, spineIndex);
        return converter.cfiToXPointer(normalizedCfi);
    }

    public XPointerResult convertCfiToXPointer(Path epubPath, String cfi) {
        return convertCfiToXPointer(epubPath.toFile(), cfi);
    }

    public String convertXPointerToCfi(File epubFile, String xpointer) {
        int spineIndex = CfiConverter.extractSpineIndex(xpointer);
        CfiConverter converter = createConverter(epubFile, spineIndex);
        return converter.xPointerToCfi(xpointer);
    }

    public String convertXPointerToCfi(Path epubPath, String xpointer) {
        return convertXPointerToCfi(epubPath.toFile(), xpointer);
    }

    public String convertXPointerRangeToCfi(File epubFile, String startXPointer, String endXPointer) {
        int startSpineIndex = extractSpineIndex(startXPointer);
        int endSpineIndex = extractSpineIndex(endXPointer);

        if (startSpineIndex != endSpineIndex) {
            throw ApiError.INVALID_INPUT.createException("Start and end XPointers must reference the same spine index");
        }

        CfiConverter converter = createConverter(epubFile, startSpineIndex);
        return converter.xPointerToCfi(startXPointer, endXPointer);
    }

    public String convertXPointerRangeToCfi(Path epubPath, String startXPointer, String endXPointer) {
        return convertXPointerRangeToCfi(epubPath.toFile(), startXPointer, endXPointer);
    }

    public String convertCfiToProgressXPointer(File epubFile, String cfi) {
        XPointerResult result = convertCfiToXPointer(epubFile, cfi);
        return CfiConverter.normalizeProgressXPointer(result.getXpointer());
    }

    public String convertCfiToProgressXPointer(Path epubPath, String cfi) {
        return convertCfiToProgressXPointer(epubPath.toFile(), cfi);
    }

    public Optional<CfiLocation> resolveCfiLocation(File epubFile, String cfi) {
        if (cfi == null || cfi.isBlank()) {
            return Optional.empty();
        }

        try {
            String normalizedCfi = normalizeContentDocumentCfi(cfi);
            CfiExpression expression = CfiParser.parse(normalizedCfi);
            int spineIndex = expression.spineIndex();
            String href = EpubContentReader.getSpineItemHref(epubFile, spineIndex);
            if (href == null || href.isBlank()) {
                return Optional.empty();
            }

            String html = EpubContentReader.getSpineItemContent(epubFile, spineIndex);
            if (html.isBlank()) {
                return Optional.empty();
            }

            Integer sourceOffset = resolveSourceOffset(getCachedDocument(epubFile, spineIndex), expression);
            Float contentSourceProgressPercent = sourceOffset == null
                    ? null
                    : clampPercent((sourceOffset / (float) Math.max(html.length(), 1)) * 100f);

            return Optional.of(new CfiLocation(href, contentSourceProgressPercent));
        } catch (Exception e) {
            log.debug("Failed to resolve chapter position from CFI: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<CfiLocation> resolveCfiLocation(Path epubPath, String cfi) {
        return resolveCfiLocation(epubPath.toFile(), cfi);
    }

    public boolean validateCfi(File epubFile, String cfi) {
        try {
            String normalizedCfi = normalizeContentDocumentCfi(cfi);
            int spineIndex = CfiConverter.extractSpineIndex(normalizedCfi);
            CfiConverter converter = createConverter(epubFile, spineIndex);
            return converter.validateCfi(normalizedCfi);
        } catch (Exception e) {
            log.debug("CFI validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(File epubFile, String xpointer) {
        try {
            int spineIndex = CfiConverter.extractSpineIndex(xpointer);
            CfiConverter converter = createConverter(epubFile, spineIndex);
            return converter.validateXPointer(xpointer);
        } catch (Exception e) {
            log.debug("XPointer validation failed: {}", e.getMessage());
            return false;
        }
    }

    public int extractSpineIndex(String cfiOrXPointer) {
        return CfiConverter.extractSpineIndex(cfiOrXPointer);
    }

    public int getSpineSize(File epubFile) {
        return EpubContentReader.getSpineSize(epubFile);
    }

    public CfiConverter createConverter(File epubFile, int spineIndex) {
        Document doc = getCachedDocument(epubFile, spineIndex);
        return new CfiConverter(new JsoupDocumentNavigator(doc), spineIndex);
    }

    public CfiConverter createConverter(Path epubPath, int spineIndex) {
        return createConverter(epubPath.toFile(), spineIndex);
    }

    private Document getCachedDocument(File epubFile, int spineIndex) {
        String cacheKey = buildCacheKey(epubFile, spineIndex);
        return documentCache.get(cacheKey, key -> {
            log.debug("Cache miss for epub spine: {} index {}", epubFile.getName(), spineIndex);
            String html = EpubContentReader.getSpineItemContent(epubFile, spineIndex);
            return Jsoup.parse(html, "", parserFor(html).setTrackPosition(true));
        });
    }

    /**
     * EPUB content documents are XHTML, which browsers and KOReader parse as XML. The HTML parser
     * would treat self-closing tags such as {@code <a id="x"/>} as open elements and nest the rest
     * of the chapter inside them, so CFI and XPointer paths would no longer match the document.
     */
    private static Parser parserFor(String content) {
        return isXhtml(content) ? Parser.xmlParser() : Parser.htmlParser();
    }

    /**
     * Only the document itself decides: an XML declaration or an XHTML namespace on the root element.
     * Text that merely mentions the namespace (e.g. a book about XHTML) does not count.
     */
    private static boolean isXhtml(String content) {
        if (XML_DECLARATION_PATTERN.matcher(content).find()) {
            return true;
        }
        int rootAttributes = findRootElementAttributes(content);
        return rootAttributes >= 0 && declaresXhtmlNamespace(content, rootAttributes);
    }

    /**
     * Skips leading whitespace, comments, the doctype and processing instructions, and returns the index just
     * after {@code <html} when the root element comes next, or -1 otherwise.
     */
    private static int findRootElementAttributes(String content) {
        int i = content.startsWith("\uFEFF") ? 1 : 0;
        while (i < content.length()) {
            if (Character.isWhitespace(content.charAt(i))) {
                i++;
            } else if (content.startsWith("<!--", i)) {
                i = indexAfter(content, "-->", i + 4);
            } else if (content.startsWith("<?", i)) {
                i = indexAfter(content, "?>", i + 2);
            } else if (content.regionMatches(true, i, "<!DOCTYPE", 0, 9)) {
                i = skipDoctype(content, i + 9);
            } else if (content.regionMatches(true, i, "<html", 0, 5)
                    && i + 5 < content.length()
                    && (Character.isWhitespace(content.charAt(i + 5)) || content.charAt(i + 5) == '>' || content.charAt(i + 5) == '/')) {
                return i + 5;
            } else {
                return -1;
            }
            if (i < 0) {
                return -1;
            }
        }
        return -1;
    }

    private static int indexAfter(String content, String terminator, int from) {
        int end = content.indexOf(terminator, from);
        return end < 0 ? -1 : end + terminator.length();
    }

    private static int skipDoctype(String content, int i) {
        boolean inInternalSubset = false;
        for (; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '[') {
                inInternalSubset = true;
            } else if (c == ']') {
                inInternalSubset = false;
            } else if (c == '>' && !inInternalSubset) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Reads the root element's attributes, respecting quoted values (a '>' inside one does not end the tag),
     * and checks for {@code xmlns="http://www.w3.org/1999/xhtml"}.
     */
    private static boolean declaresXhtmlNamespace(String content, int i) {
        int length = content.length();
        while (i < length) {
            while (i < length && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            if (i >= length || content.charAt(i) == '>' || content.charAt(i) == '/') {
                return false;
            }
            int nameStart = i;
            while (i < length && !Character.isWhitespace(content.charAt(i)) && "=>/".indexOf(content.charAt(i)) < 0) {
                i++;
            }
            String name = content.substring(nameStart, i);
            while (i < length && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            String value = null;
            if (i < length && content.charAt(i) == '=') {
                i++;
                while (i < length && Character.isWhitespace(content.charAt(i))) {
                    i++;
                }
                if (i < length && (content.charAt(i) == '"' || content.charAt(i) == '\'')) {
                    int end = content.indexOf(content.charAt(i), i + 1);
                    if (end < 0) {
                        return false;
                    }
                    value = content.substring(i + 1, end);
                    i = end + 1;
                } else {
                    int valueStart = i;
                    while (i < length && !Character.isWhitespace(content.charAt(i)) && content.charAt(i) != '>') {
                        i++;
                    }
                    value = content.substring(valueStart, i);
                }
            }
            if ("xmlns".equals(name)) {
                return value != null && XHTML_NAMESPACE.equals(value.trim());
            }
        }
        return false;
    }

    private String buildCacheKey(File epubFile, int spineIndex) {
        try {
            return epubFile.getCanonicalPath() + ":" + spineIndex;
        } catch (IOException e) {
            return epubFile.getAbsolutePath() + ":" + spineIndex;
        }
    }

    public void evictCache(File epubFile) {
        try {
            String pathPrefix = epubFile.getCanonicalPath() + ":";
            documentCache.asMap().keySet().removeIf(key -> key.startsWith(pathPrefix));
        } catch (IOException e) {
            String pathPrefix = epubFile.getAbsolutePath() + ":";
            documentCache.asMap().keySet().removeIf(key -> key.startsWith(pathPrefix));
        }
    }

    public void clearCache() {
        documentCache.invalidateAll();
    }

    private String normalizeContentDocumentCfi(String cfi) {
        if (cfi == null) {
            return null;
        }
        return CFI_CLEANER_PATTERN.matcher(cfi).replaceFirst("!");
    }

    private Integer resolveSourceOffset(Document document, CfiExpression expression) {
        if (document == null || expression == null) {
            return null;
        }

        List<CfiExpression.PathStep> targetSteps = expression.isRange() && expression.rangeEndSteps() != null
                ? expression.rangeEndSteps()
                : expression.contentSteps();
        Integer textOffset = expression.isRange() && expression.rangeEndOffset() != null
                ? expression.rangeEndOffset()
                : expression.charOffset();

        Element element = resolveElement(document.body(), targetSteps);
        if (element == null) {
            return null;
        }

        if (textOffset != null) {
            Integer textSourceOffset = resolveTextOffsetSourcePosition(element, textOffset);
            if (textSourceOffset != null) {
                return textSourceOffset;
            }
        }

        return element.sourceRange() == null ? null : Math.max(element.sourceRange().startPos(), 0);
    }

    private Element resolveElement(Element root, List<CfiExpression.PathStep> steps) {
        Element current = root;
        if (current == null || steps == null || steps.isEmpty()) {
            return current;
        }

        for (int i = 0; i < steps.size(); i++) {
            CfiExpression.PathStep step = steps.get(i);
            if (!step.targetsElement()) {
                break;
            }

            int childIndex = step.childElementIndex();
            if (childIndex < 0 || childIndex >= current.childrenSize()) {
                return null;
            }

            current = current.child(childIndex);
        }

        return current;
    }

    private Integer resolveTextOffsetSourcePosition(Element element, int cfiOffset) {
        List<TextNode> textNodes = new ArrayList<>();
        collectTextNodes(element, textNodes);

        int totalChars = 0;
        for (TextNode textNode : textNodes) {
            String nodeText = textNode.text();
            int nodeLength = nodeText.length();

            if (totalChars + nodeLength >= cfiOffset) {
                if (textNode.sourceRange() == null) {
                    return null;
                }

                int offsetInNode = Math.max(cfiOffset - totalChars, 0);
                int startPos = Math.max(textNode.sourceRange().startPos(), 0);
                return startPos + offsetInNode;
            }

            totalChars += nodeLength;
        }

        return element.sourceRange() == null ? null : Math.max(element.sourceRange().startPos(), 0);
    }

    private void collectTextNodes(Element element, List<TextNode> textNodes) {
        for (TextNode textNode : element.textNodes()) {
            if (!textNode.text().isEmpty()) {
                textNodes.add(textNode);
            }
        }

        for (Element child : element.children()) {
            collectTextNodes(child, textNodes);
        }
    }

    private Float clampPercent(float percent) {
        return Math.clamp(percent, 0f, 100f);
    }

    public record CfiLocation(String href, Float contentSourceProgressPercent) {
    }
}
