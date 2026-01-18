package com.adityachandel.booklore.util.koreader;

import com.adityachandel.booklore.util.epub.EpubContentReader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
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
                .maximumSize(50)
                .expireAfterAccess(Duration.ofMinutes(10))
                .softValues()
                .build();
    }

    public CfiConvertor.XPointerResult convertCfiToXPointer(File epubFile, String cfi) {
        int spineIndex = CfiConvertor.extractSpineIndex(cfi);
        CfiConvertor converter = createConverter(epubFile, spineIndex);
        return converter.cfiToXPointer(cfi);
    }

    public CfiConvertor.XPointerResult convertCfiToXPointer(Path epubPath, String cfi) {
        return convertCfiToXPointer(epubPath.toFile(), cfi);
    }

    public String convertXPointerToCfi(File epubFile, String xpointer) {
        int spineIndex = CfiConvertor.extractSpineIndex(xpointer);
        CfiConvertor converter = createConverter(epubFile, spineIndex);
        return converter.xPointerToCfi(xpointer);
    }

    public String convertXPointerToCfi(Path epubPath, String xpointer) {
        return convertXPointerToCfi(epubPath.toFile(), xpointer);
    }

    public String convertXPointerRangeToCfi(File epubFile, String startXPointer, String endXPointer) {
        int spineIndex = CfiConvertor.extractSpineIndex(startXPointer);
        CfiConvertor converter = createConverter(epubFile, spineIndex);
        return converter.xPointerToCfi(startXPointer, endXPointer);
    }

    public String convertXPointerRangeToCfi(Path epubPath, String startXPointer, String endXPointer) {
        return convertXPointerRangeToCfi(epubPath.toFile(), startXPointer, endXPointer);
    }

    public String convertCfiToProgressXPointer(File epubFile, String cfi) {
        CfiConvertor.XPointerResult result = convertCfiToXPointer(epubFile, cfi);
        return CfiConvertor.normalizeProgressXPointer(result.getXpointer());
    }

    public String convertCfiToProgressXPointer(Path epubPath, String cfi) {
        return convertCfiToProgressXPointer(epubPath.toFile(), cfi);
    }

    public boolean validateCfi(File epubFile, String cfi) {
        try {
            int spineIndex = CfiConvertor.extractSpineIndex(cfi);
            CfiConvertor converter = createConverter(epubFile, spineIndex);
            return converter.validateCfi(cfi);
        } catch (Exception e) {
            log.debug("CFI validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateXPointer(File epubFile, String xpointer) {
        try {
            int spineIndex = CfiConvertor.extractSpineIndex(xpointer);
            CfiConvertor converter = createConverter(epubFile, spineIndex);
            return converter.validateXPointer(xpointer);
        } catch (Exception e) {
            log.debug("XPointer validation failed: {}", e.getMessage());
            return false;
        }
    }

    public int extractSpineIndex(String cfiOrXPointer) {
        return CfiConvertor.extractSpineIndex(cfiOrXPointer);
    }

    public int getSpineSize(File epubFile) {
        return EpubContentReader.getSpineSize(epubFile);
    }

    public CfiConvertor createConverter(File epubFile, int spineIndex) {
        Document doc = getCachedDocument(epubFile, spineIndex);
        return new CfiConvertor(doc, spineIndex);
    }

    public CfiConvertor createConverter(Path epubPath, int spineIndex) {
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
