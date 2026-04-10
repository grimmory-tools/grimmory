package org.booklore.util.koreader;

import org.booklore.exception.ApiError;
import org.booklore.util.epub.EpubContentReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.epub4j.cfi.CfiConverter;
import org.grimmory.epub4j.cfi.XPointerResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Service
public class EpubCfiService {

    private final Cache<String, Document> documentCache;

    public EpubCfiService() {
        this.documentCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();
    }

    public XPointerResult convertCfiToXPointer(File epubFile, String cfi) {
        int spineIndex = CfiConverter.extractSpineIndex(cfi);
        CfiConverter converter = createConverter(epubFile, spineIndex);
        return converter.cfiToXPointer(cfi);
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

    public boolean validateCfi(File epubFile, String cfi) {
        try {
            int spineIndex = CfiConverter.extractSpineIndex(cfi);
            CfiConverter converter = createConverter(epubFile, spineIndex);
            return converter.validateCfi(cfi);
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
            return Jsoup.parse(html);
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
}
