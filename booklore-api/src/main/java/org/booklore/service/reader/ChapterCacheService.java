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
import java.util.concurrent.StructuredTaskScope;

/**
 * Service for managing the on-disk extraction cache for reader chapters.
 * Leverages Java 25 Virtual Threads for concurrent page extraction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterCacheService {

    private final AppProperties appProperties;
    private final ArchiveService archiveService;

    /**
     * Ensures all pages of a CBX archive are extracted to the disk cache.
     * Uses StructuredTaskScope to extract pages concurrently.
     */
    public void prepareCbxCache(Long bookId, Path cbxPath, List<String> entries) throws IOException {
        Path cacheDir = getCacheDir(bookId);
        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        // Only extract if the cache is empty or stale
        if (isCacheStale(cacheDir, cbxPath)) {
            log.info("Populating disk cache for book {}: {} pages", bookId, entries.size());
            
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < entries.size(); i++) {
                    final String entryName = entries.get(i);
                    final int pageNum = i + 1;
                    scope.fork(() -> {
                        Path target = cacheDir.resolve("page_" + pageNum + ".jpg");
                        if (!Files.exists(target)) {
                            try (var out = Files.newOutputStream(target)) {
                                archiveService.transferEntryTo(cbxPath, entryName, out);
                            }
                        }
                        return null;
                    });
                }
                scope.join();
            } catch (Exception e) {
                log.error("Concurrent extraction failed for book {}: {}", bookId, e.getMessage());
                throw new IOException("Failed to populate chapter cache", e);
            }
            
            // Mark cache as fresh by setting its mtime to match the archive
            Files.setLastModifiedTime(cacheDir, Files.getLastModifiedTime(cbxPath));
        }
    }

    public Path getCachedPage(Long bookId, int pageNumber) {
        return getCacheDir(bookId).resolve("page_" + pageNumber + ".jpg");
    }

    public boolean hasPage(Long bookId, int pageNumber) {
        return Files.exists(getCachedPage(bookId, pageNumber));
    }

    private Path getCacheDir(Long bookId) {
        return Paths.get(appProperties.getPathConfig(), "cache", "chapters", String.valueOf(bookId));
    }

    private boolean isCacheStale(Path cacheDir, Path sourcePath) throws IOException {
        if (!Files.exists(cacheDir)) return true;
        try (var stream = Files.list(cacheDir)) {
            if (stream.findAny().isEmpty()) return true;
        }
        return Files.getLastModifiedTime(cacheDir).toMillis() != Files.getLastModifiedTime(sourcePath).toMillis();
    }
}
