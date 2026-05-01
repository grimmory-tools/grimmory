package org.booklore.util.koreader;

import org.booklore.exception.ApiError;
import org.booklore.util.epub.EpubContentReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.epub4j.cfi.CfiExpression;
import org.grimmory.epub4j.cfi.CfiConverter;
import org.grimmory.epub4j.cfi.CfiParser;
import org.grimmory.epub4j.cfi.XPointerResult;
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

    private static final Pattern CFI_CLEANER_PATTERN = Pattern.compile("!/4(?=/)");
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
            return Jsoup.parse(html, "", Parser.htmlParser().setTrackPosition(true));
        });
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

            if (i == 0 && step.position() == 4) {
                continue;
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
        return Math.max(0f, Math.min(percent, 100f));
    }

    public record CfiLocation(String href, Float contentSourceProgressPercent) {
    }
}
