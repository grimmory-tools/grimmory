package org.booklore.service.reader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.CbxPageDimension;
import org.booklore.model.dto.response.CbxPageInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.ArchiveService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CbxReaderService {

    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".avif", ".heic", ".gif", ".bmp"};
    private static final int MAX_CACHE_ENTRIES = 50;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+)|(\\D+)");
    private static final Set<String> SYSTEM_FILES = Set.of(".ds_store", "thumbs.db", "desktop.ini");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    private final BookRepository bookRepository;
    private final Cache<String, CachedArchiveMetadata> archiveCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private final ArchiveService archiveService;
    private final ChapterCacheService chapterCacheService;

    // L1 Cache: Open ZipFile handles for active reading sessions (TTL 30m)
    @Setter
    private Cache<String, ZipFile> zipHandleCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .expireAfterAccess(Duration.ofMinutes(30))
            .removalListener((String _, ZipFile value, RemovalCause _) -> {
                try {
                    value.close();
                } catch (IOException _) {
                }
            })
            .build();

    private record CachedArchiveMetadata(List<String> imageEntries, List<CbxPageDimension> pageDimensions, long lastModified) {
        CachedArchiveMetadata {
            imageEntries = List.copyOf(imageEntries);
            pageDimensions = pageDimensions != null ? List.copyOf(pageDimensions) : null;
        }
    }

    public void initCache(Long bookId, String bookType) throws IOException {
        Path cbxPath = getBookPath(bookId, bookType);
        CachedArchiveMetadata metadata = getCachedMetadata(cbxPath);
        String cacheKey = getCacheKey(bookId, bookType, metadata.lastModified());
        chapterCacheService.prepareCbxCache(cacheKey, cbxPath, metadata.imageEntries());

        if (metadata.pageDimensions() == null) {
            List<CbxPageDimension> dimensions = computeDimensionsFromDiskCache(cacheKey, metadata.imageEntries().size());
            CachedArchiveMetadata updated = new CachedArchiveMetadata(metadata.imageEntries(), dimensions, metadata.lastModified());
            archiveCache.put(cbxPath.toString(), updated);
        }
    }

    private List<CbxPageDimension> computeDimensionsFromDiskCache(String cacheKey, int pageCount) {
        List<CbxPageDimension> dimensions = new ArrayList<>();
        for (int i = 1; i <= pageCount; i++) {
            Path cachedPage = chapterCacheService.getCachedPage(cacheKey, i);
            try (ImageInputStream iis = ImageIO.createImageInputStream(cachedPage.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        dimensions.add(CbxPageDimension.builder().pageNumber(i).width(width).height(height).wide(width > height).build());
                        continue;
                    } finally {
                        reader.dispose();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read dimensions for cached page {}: {}", i, e.getMessage());
            }
            dimensions.add(CbxPageDimension.builder().pageNumber(i).width(0).height(0).wide(false).build());
        }
        return dimensions;
    }

    public List<Integer> getAvailablePages(Long bookId) {
        return getAvailablePages(bookId, null);
    }

    public List<Integer> getAvailablePages(Long bookId, String bookType) {
        Path cbxPath = getBookPath(bookId, bookType);
        try {
            List<String> imageEntries = getImageEntriesFromArchiveCached(cbxPath);
            return IntStream.rangeClosed(1, imageEntries.size())
                    .boxed()
                    .toList();
        } catch (IOException e) {
            log.error("Failed to read archive for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read archive: " + e.getMessage());
        }
    }

    public List<CbxPageInfo> getPageInfo(Long bookId) {
        return getPageInfo(bookId, null);
    }

    public List<CbxPageInfo> getPageInfo(Long bookId, String bookType) {
        Path cbxPath = getBookPath(bookId, bookType);
        try {
            List<String> imageEntries = getImageEntriesFromArchiveCached(cbxPath);
            List<CbxPageInfo> pageInfoList = new ArrayList<>();
            for (int i = 0; i < imageEntries.size(); i++) {
                String entryPath = imageEntries.get(i);
                String displayName = extractDisplayName(entryPath);
                pageInfoList.add(CbxPageInfo.builder()
                        .pageNumber(i + 1)
                        .displayName(displayName)
                        .build());
            }
            return pageInfoList;
        } catch (IOException e) {
            log.error("Failed to read archive for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read archive: " + e.getMessage());
        }
    }

    public List<CbxPageDimension> getPageDimensions(Long bookId, String bookType) {
        Path cbxPath = getBookPath(bookId, bookType);
        try {
            CachedArchiveMetadata metadata = getCachedMetadata(cbxPath);
            if (metadata.pageDimensions() != null) {
                return metadata.pageDimensions();
            }
            
            List<String> imageEntries = metadata.imageEntries();
            List<CbxPageDimension> dimensions = new ArrayList<>();
            for (int i = 0; i < imageEntries.size(); i++) {
                String entryName = imageEntries.get(i);
                CbxPageDimension dim = readEntryDimension(cbxPath, entryName, i + 1);
                dimensions.add(dim);
            }
            
            CachedArchiveMetadata updatedMetadata = new CachedArchiveMetadata(metadata.imageEntries(), dimensions, metadata.lastModified());
            archiveCache.put(cbxPath.toString(), updatedMetadata);
            
            return dimensions;
        } catch (IOException e) {
            log.error("Failed to read page dimensions for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read page dimensions: " + e.getMessage());
        }
    }

    private CbxPageDimension readEntryDimension(Path cbxPath, String entryName, int pageNumber) {
        try {
            byte[] imageBytes = archiveService.getEntryBytes(cbxPath, entryName);
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        return CbxPageDimension.builder()
                                .pageNumber(pageNumber)
                                .width(width)
                                .height(height)
                                .wide(width > height)
                                .build();
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read dimensions for page {} (entry: {}): {}", pageNumber, entryName, e.getMessage());
        }
        return CbxPageDimension.builder()
                .pageNumber(pageNumber)
                .width(0)
                .height(0)
                .wide(false)
                .build();
    }

    private String extractDisplayName(String entryPath) {
        String fileName = baseName(entryPath);
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    public void streamPageImage(Long bookId, int page, OutputStream outputStream) throws IOException {
        streamPageImage(bookId, null, page, outputStream);
    }

    public void streamPageImage(Long bookId, String bookType, int page, OutputStream outputStream) throws IOException {
        Path cbxPath = getBookPath(bookId, bookType);
        CachedArchiveMetadata metadata = getCachedMetadata(cbxPath);
        validatePageRequest(bookId, page, metadata.imageEntries());

        // Tier 1: Check L1 Memory Map (OS File Cache) via open ZipFile
        // This is the fastest path for ZIP/CBZ
        try {
            java.util.zip.ZipFile zip = getZipFile(cbxPath, metadata.lastModified());
            if (zip != null) {
                String entryName = metadata.imageEntries().get(page - 1);
                java.util.zip.ZipEntry entry = zip.getEntry(entryName);
                if (entry != null) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        is.transferTo(outputStream);
                        return;
                    }
                }
            }
        } catch (IOException e) {
            log.trace("L1 Zip cache miss or unsupported format for book {}: {}", bookId, e.getMessage());
        }

        // Tier 2: Check L3 Disk Cache (extracted files)
        String cacheKey = getCacheKey(bookId, bookType, metadata.lastModified());
        if (chapterCacheService.hasPage(cacheKey, page)) {
            Path cached = chapterCacheService.getCachedPage(cacheKey, page);
            Files.copy(cached, outputStream);
            return;
        }

        // Tier 3: Fallback to full extraction/stream (slowest)
        String entryName = metadata.imageEntries().get(page - 1);
        archiveService.transferEntryTo(cbxPath, entryName, outputStream);
    }

    private ZipFile getZipFile(Path cbxPath, long lastModified) {
        String cacheKey = cbxPath.toString() + ":" + lastModified;
        return zipHandleCache.get(cacheKey, _ -> {
            try {
                if (cbxPath.toString().toLowerCase().endsWith(".cbz") || cbxPath.toString().toLowerCase().endsWith(".zip")) {
                    return new java.util.zip.ZipFile(cbxPath.toFile());
                }
            } catch (IOException e) {
                log.warn("Failed to open ZipFile for {}: {}", cbxPath, e.getMessage());
            }
            return null;
        });
    }

    private String getCacheKey(Long bookId, String bookType, long lastModified) {
        if (bookType != null) {
            // Ensure we use the safe enum name to prevent path traversal
            BookFileType type = BookFileType.fromName(bookType)
                    .orElseThrow(() -> ApiError.INVALID_INPUT.createException("Invalid book type: " + bookType));
            return bookId + "_" + type.name() + "_" + lastModified;
        }
        return bookId + "_" + lastModified;
    }

    private Path getBookPath(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdForStreaming(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookType != null) {
            BookFileType requestedType = BookFileType.fromName(bookType)
                    .orElseThrow(() -> ApiError.INVALID_INPUT.createException("Invalid book type: " + bookType));
            BookFileEntity bookFile = bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
            return bookFile.getFullFilePath();
        }
        return FileUtils.getBookFullPath(bookEntity);
    }

    private void validatePageRequest(Long bookId, int page, List<String> imageEntries) throws FileNotFoundException {
        if (imageEntries.isEmpty()) {
            throw new FileNotFoundException("No image files found for book: " + bookId);
        }
        if (page < 1 || page > imageEntries.size()) {
            throw new FileNotFoundException("Page " + page + " out of range [1-" + imageEntries.size() + "]");
        }
    }

    private CachedArchiveMetadata getCachedMetadata(Path cbxPath) throws IOException {
        String cacheKey = cbxPath.toString();
        long currentModified = Files.getLastModifiedTime(cbxPath).toMillis();
        CachedArchiveMetadata cached = archiveCache.getIfPresent(cacheKey);
        if (cached != null && cached.lastModified() == currentModified) {
            log.debug("Cache hit for archive: {}", cbxPath.getFileName());
            return cached;
        }
        log.debug("Cache miss for archive: {}, scanning...", cbxPath.getFileName());
        CachedArchiveMetadata newMetadata = scanArchiveMetadata(cbxPath);
        archiveCache.put(cacheKey, newMetadata);
        return newMetadata;
    }

    private List<String> getImageEntriesFromArchiveCached(Path cbxPath) throws IOException {
        return getCachedMetadata(cbxPath).imageEntries();
    }

    private CachedArchiveMetadata scanArchiveMetadata(Path cbxPath) throws IOException {
        long lastModified = Files.getLastModifiedTime(cbxPath).toMillis();

        List<String> entries = getImageEntries(cbxPath);
        return new CachedArchiveMetadata(entries, null, lastModified);
    }

    private List<String> getImageEntries(Path cbxPath) throws IOException {
        try {
            return archiveService.streamEntryNames(cbxPath)
                    .filter(this::isImageFile)
                    .sorted(CbxReaderService::sortNaturally)
                    .toList();

        } catch (Exception e) {
            throw new IOException("Failed to read archive: " + e.getMessage(), e);
        }
    }

    private boolean isImageFile(String name) {
        if (!isContentEntry(name)) {
            return false;
        }
        String lower = name.toLowerCase().replace('\\', '/');
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private boolean isContentEntry(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/") || normalized.contains("/__MACOSX/")) {
            return false;
        }
        // Prevent path traversal: reject any entry whose path contains ".." as a component.
        // Checks split-by-/ to catch "foo/..", ".." alone, and not just "../" (with trailing slash).
        for (String component : normalized.split("/", -1)) {
            if ("..".equals(component)) {
                return false;
            }
        }
        String baseName = baseName(normalized).toLowerCase();
        if (baseName.startsWith("._") || !baseName.isEmpty() && baseName.charAt(0) == '.') {
            return false;
        }
        return !SYSTEM_FILES.contains(baseName);
    }

    private String baseName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static int sortNaturally(String s1, String s2) {
        Matcher m1 = NUMERIC_PATTERN.matcher(s1);
        Matcher m2 = NUMERIC_PATTERN.matcher(s2);
        while (m1.find() && m2.find()) {
            String part1 = m1.group();
            String part2 = m2.group();
            if (DIGIT_PATTERN.matcher(part1).matches() && DIGIT_PATTERN.matcher(part2).matches()) {
                int cmp = Integer.compare(
                        Integer.parseInt(part1),
                        Integer.parseInt(part2)
                );
                if (cmp != 0) return cmp;
            } else {
                int cmp = part1.compareToIgnoreCase(part2);
                if (cmp != 0) return cmp;
            }
        }
        return s1.compareToIgnoreCase(s2);
    }
}
