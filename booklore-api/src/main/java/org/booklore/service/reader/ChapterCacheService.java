package org.booklore.service.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.AppProperties;
import org.booklore.service.ArchiveService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing the on-disk extraction cache for reader chapters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterCacheService {

    private static final long MTIME_TOLERANCE_MS = 2000;

    private final AppProperties appProperties;
    private final ArchiveService archiveService;
    private final ConcurrentHashMap<String, ReentrantLock> cacheLocks = new ConcurrentHashMap<>();

    /**
     * Ensures all pages of a CBX archive are extracted to the disk cache.
     * Extracts pages sequentially to avoid concurrent native libarchive access
     * which can cause SIGSEGV / out-of-memory crashes in the native heap.
     */
    public void prepareCbxCache(String cacheKey, Path cbxPath, List<String> entries) throws IOException {
        ReentrantLock lock = cacheLocks.computeIfAbsent(cacheKey, _ -> new ReentrantLock());
        lock.lock();
        try {
            Path cacheDir = getCacheDir(cacheKey);
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }

            // Only extract if the cache is empty or stale
            if (isCacheStale(cacheDir, cbxPath)) {
                log.info("Populating disk cache for {}: {} pages", cacheKey, entries.size());

                for (int i = 0; i < entries.size(); i++) {
                    Path target = cacheDir.resolve("page_" + (i + 1) + ".jpg");
                    if (!Files.exists(target)) {
                        try (var out = Files.newOutputStream(target)) {
                            archiveService.transferEntryTo(cbxPath, entries.get(i), out);
                        }
                    }
                }

                // Mark cache as fresh by setting its mtime to match the archive
                Files.setLastModifiedTime(cacheDir, Files.getLastModifiedTime(cbxPath));
            }
        } finally {
            lock.unlock();
        }
    }

    public Path getCachedPage(String cacheKey, int pageNumber) {
        return getCacheDir(cacheKey).resolve("page_" + pageNumber + ".jpg");
    }

    public boolean hasPage(String cacheKey, int pageNumber) {
        Path pagePath = getCachedPage(cacheKey, pageNumber);
        return Files.exists(pagePath);
    }

    private Path getCacheDir(String cacheKey) {
        if (cacheKey == null || cacheKey.contains("..") || cacheKey.contains("/") || cacheKey.contains("\\")) {
            throw org.booklore.exception.ApiError.INVALID_INPUT.createException("Invalid cache key: " + cacheKey);
        }
        return Paths.get(appProperties.getPathConfig(), "cache", "chapters", cacheKey);
    }

    private boolean isCacheStale(Path cacheDir, Path sourcePath) throws IOException {
        if (!Files.exists(cacheDir)) return true;
        try (var stream = Files.list(cacheDir)) {
            if (stream.findAny().isEmpty()) return true;
        }
        long cacheMtime = Files.getLastModifiedTime(cacheDir).toMillis();
        long sourceMtime = Files.getLastModifiedTime(sourcePath).toMillis();
        return Math.abs(cacheMtime - sourceMtime) > MTIME_TOLERANCE_MS;
    }
}
