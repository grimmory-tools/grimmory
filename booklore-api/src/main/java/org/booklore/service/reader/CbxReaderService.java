package org.booklore.service.reader;

import lombok.RequiredArgsConstructor;
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
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final Map<String, CachedArchiveMetadata> archiveCache = new ConcurrentHashMap<>();

    private final ArchiveService archiveService;

    private static class CachedArchiveMetadata {
        final List<String> imageEntries;
        final long lastModified;
        volatile long lastAccessed;

        CachedArchiveMetadata(List<String> imageEntries, long lastModified) {
            this.imageEntries = List.copyOf(imageEntries);
            this.lastModified = lastModified;
            this.lastAccessed = System.currentTimeMillis();
        }
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
                    .collect(Collectors.toList());
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

    public List<CbxPageDimension> getPageDimensions(Long bookId) {
        return getPageDimensions(bookId, null);
    }

    public List<CbxPageDimension> getPageDimensions(Long bookId, String bookType) {
        Path cbxPath = getBookPath(bookId, bookType);
        try {
            CachedArchiveMetadata metadata = getCachedMetadata(cbxPath);
            List<String> imageEntries = metadata.imageEntries;
            List<CbxPageDimension> dimensions = new ArrayList<>();
            for (int i = 0; i < imageEntries.size(); i++) {
                String entryName = imageEntries.get(i);
                CbxPageDimension dim = readEntryDimension(cbxPath, entryName, i + 1);
                dimensions.add(dim);
            }
            return dimensions;
        } catch (IOException e) {
            log.error("Failed to read page dimensions for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read page dimensions: " + e.getMessage());
        }
    }

    private CbxPageDimension readEntryDimension(Path cbxPath, String entryName, int pageNumber) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            archiveService.transferEntryTo(cbxPath, entryName, baos);
            byte[] imageBytes = baos.toByteArray();
            try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageBytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(iis);
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
        } catch (IOException e) {
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
        validatePageRequest(bookId, page, metadata.imageEntries);
        String entryName = metadata.imageEntries.get(page - 1);
        archiveService.transferEntryTo(cbxPath, entryName, outputStream);
    }

    private Path getBookPath(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
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
        CachedArchiveMetadata cached = archiveCache.get(cacheKey);
        if (cached != null && cached.lastModified == currentModified) {
            cached.lastAccessed = System.currentTimeMillis();
            log.debug("Cache hit for archive: {}", cbxPath.getFileName());
            return cached;
        }
        log.debug("Cache miss for archive: {}, scanning...", cbxPath.getFileName());
        CachedArchiveMetadata newMetadata = scanArchiveMetadata(cbxPath);
        archiveCache.put(cacheKey, newMetadata);
        evictOldestCacheEntries();
        return newMetadata;
    }

    private List<String> getImageEntriesFromArchiveCached(Path cbxPath) throws IOException {
        return getCachedMetadata(cbxPath).imageEntries;
    }

    private void evictOldestCacheEntries() {
        if (archiveCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        List<String> keysToRemove = archiveCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessed))
                .limit(archiveCache.size() - MAX_CACHE_ENTRIES)
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(key -> {
            archiveCache.remove(key);
            log.debug("Evicted cache entry: {}", key);
        });
    }

    private CachedArchiveMetadata scanArchiveMetadata(Path cbxPath) throws IOException {
        long lastModified = Files.getLastModifiedTime(cbxPath).toMillis();

        List<String> entries = getImageEntries(cbxPath);
        return new CachedArchiveMetadata(entries, lastModified);
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
        if (baseName.startsWith("._") || baseName.startsWith(".")) {
            return false;
        }
        if (SYSTEM_FILES.contains(baseName)) {
            return false;
        }
        return true;
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
