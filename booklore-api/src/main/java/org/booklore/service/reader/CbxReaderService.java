package org.booklore.service.reader;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.pdfbox.io.IOUtils;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.CbxPageInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.util.ArchiveUtils;
import org.booklore.util.FileUtils;
import org.booklore.util.UnrarHelper;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".avif", ".heic"};
    private static final Charset[] ENCODINGS_TO_TRY = {
            StandardCharsets.UTF_8,
            Charset.forName("Shift_JIS"),
            StandardCharsets.ISO_8859_1,
            Charset.forName("CP437"),
            Charset.forName("MS932")
    };
    private static final int MAX_CACHE_ENTRIES = 50;
    private static final int BUFFER_SIZE = 8192;
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+)|(\\D+)");
    private static final Set<String> SYSTEM_FILES = Set.of(".ds_store", "thumbs.db", "desktop.ini");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    private final BookRepository bookRepository;
    private final Map<String, CachedArchiveMetadata> archiveCache = new ConcurrentHashMap<>();

    private static class CachedArchiveMetadata {
        final List<String> imageEntries;
        final long lastModified;
        final Charset successfulEncoding;
        final boolean useUnicodeExtraFields;
        volatile long lastAccessed;

        CachedArchiveMetadata(List<String> imageEntries, long lastModified, Charset successfulEncoding, boolean useUnicodeExtraFields) {
            this.imageEntries = List.copyOf(imageEntries);
            this.lastModified = lastModified;
            this.successfulEncoding = successfulEncoding;
            this.useUnicodeExtraFields = useUnicodeExtraFields;
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
        streamEntryFromArchive(cbxPath, entryName, outputStream, metadata);
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
        String bookFullPath = FileUtils.getBookFullPath(bookEntity);
        return Path.of(bookFullPath);
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
        ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(cbxPath.toFile());
        return switch (type) {
            case ZIP -> scanZipMetadata(cbxPath, lastModified);
            case SEVEN_ZIP -> {
                List<String> entries = getImageEntriesFrom7z(cbxPath);
                yield new CachedArchiveMetadata(entries, lastModified, null, false);
            }
            case RAR -> {
                List<String> entries = getImageEntriesFromRar(cbxPath);
                yield new CachedArchiveMetadata(entries, lastModified, null, false);
            }
            default -> throw new IOException("Unsupported archive format: " + cbxPath.getFileName());
        };
    }

    private CachedArchiveMetadata scanZipMetadata(Path cbxPath, long lastModified) throws IOException {
        String cacheKey = cbxPath.toString();
        CachedArchiveMetadata oldCache = archiveCache.get(cacheKey);
        if (oldCache != null && oldCache.successfulEncoding != null) {
            try {
                List<String> entries = getImageEntriesFromZipWithEncoding(cbxPath, oldCache.successfulEncoding, true, oldCache.useUnicodeExtraFields);
                return new CachedArchiveMetadata(entries, lastModified, oldCache.successfulEncoding, oldCache.useUnicodeExtraFields);
            } catch (Exception e) {
                log.debug("Cached encoding {} with useUnicode={} failed, trying others", oldCache.successfulEncoding, oldCache.useUnicodeExtraFields);
            }
        }

        // Try combinations per encoding
        for (Charset encoding : ENCODINGS_TO_TRY) {
            // Priority 1: Fast path, Unicode Enabled
            try {
                List<String> entries = getImageEntriesFromZipWithEncoding(cbxPath, encoding, true, true);
                return new CachedArchiveMetadata(entries, lastModified, encoding, true);
            } catch (Exception e) {
                log.trace("ZIP strategy failed (Fast, Unicode, {}): {}", encoding, e.getMessage());
            }
            
            // Priority 2: Slow path, Unicode Enabled
            try {
                List<String> entries = getImageEntriesFromZipWithEncoding(cbxPath, encoding, false, true);
                return new CachedArchiveMetadata(entries, lastModified, encoding, true);
            } catch (Exception e) {
                log.trace("ZIP strategy failed (Slow, Unicode, {}): {}", encoding, e.getMessage());
            }

            // Priority 3: Fast path, Unicode Disabled (Fallback)
            try {
                List<String> entries = getImageEntriesFromZipWithEncoding(cbxPath, encoding, true, false);
                return new CachedArchiveMetadata(entries, lastModified, encoding, false);
            } catch (Exception e) {
                log.trace("ZIP strategy failed (Fast, No-Unicode, {}): {}", encoding, e.getMessage());
            }

            // Priority 4: Slow path, Unicode Disabled (Fallback)
            try {
                List<String> entries = getImageEntriesFromZipWithEncoding(cbxPath, encoding, false, false);
                return new CachedArchiveMetadata(entries, lastModified, encoding, false);
            } catch (Exception e) {
                log.trace("ZIP strategy failed (Slow, No-Unicode, {}): {}", encoding, e.getMessage());
            }
        }

        throw new IOException("Unable to read ZIP archive with any supported encoding");
    }

    private List<String> getImageEntriesFromZipWithEncoding(Path cbxPath, Charset charset, boolean useFastPath, boolean useUnicodeExtraFields) throws IOException {
        try (org.apache.commons.compress.archivers.zip.ZipFile zipFile =
                     org.apache.commons.compress.archivers.zip.ZipFile.builder()
                             .setPath(cbxPath)
                             .setCharset(charset)
                             .setUseUnicodeExtraFields(useUnicodeExtraFields)
                             .setIgnoreLocalFileHeader(useFastPath)
                             .get()) {
            List<String> entries = new ArrayList<>();
            Enumeration<ZipArchiveEntry> enumeration = zipFile.getEntries();
            while (enumeration.hasMoreElements()) {
                ZipArchiveEntry entry = enumeration.nextElement();
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    entries.add(entry.getName());
                }
            }
            sortNaturally(entries);
            return entries;
        }
    }

    private void streamEntryFromZip(Path cbxPath, String entryName, OutputStream outputStream, CachedArchiveMetadata metadata) throws IOException {
        Charset encoding = metadata != null ? metadata.successfulEncoding : null;
        boolean useUnicode = metadata != null && metadata.useUnicodeExtraFields;

        if (encoding != null) {
            if (tryStreamEntry(cbxPath, entryName, outputStream, encoding, true, useUnicode)) return;
            if (tryStreamEntry(cbxPath, entryName, outputStream, encoding, false, useUnicode)) return;
                    }
        
        for (Charset charset : ENCODINGS_TO_TRY) {
            if (charset.equals(encoding)) continue; // Skip failed cached encoding if we want, or retry it with different flags? 
            
            if (tryStreamEntry(cbxPath, entryName, outputStream, charset, true, true)) return;
            if (tryStreamEntry(cbxPath, entryName, outputStream, charset, false, true)) return;
            if (tryStreamEntry(cbxPath, entryName, outputStream, charset, true, false)) return;
            if (tryStreamEntry(cbxPath, entryName, outputStream, charset, false, false)) return;
        }

        throw new IOException("Unable to find entry in ZIP archive: " + entryName);
    }
    
    private boolean tryStreamEntry(Path cbxPath, String entryName, OutputStream outputStream, Charset charset, boolean useFastPath, boolean useUnicode) {
        try {
            if (streamEntryFromZipWithEncoding(cbxPath, entryName, outputStream, charset, useFastPath, useUnicode)) {
                return true;
            }
        } catch (Exception e) {
             log.trace("Stream strategy failed ({}, Fast={}, Unicode={}): {}", charset, useFastPath, useUnicode, e.getMessage());
        }
        return false;
    }
    


    private void streamEntryFromArchive(Path cbxPath, String entryName, OutputStream outputStream, CachedArchiveMetadata metadata) throws IOException {
        ArchiveUtils.ArchiveType type = ArchiveUtils.detectArchiveType(cbxPath.toFile());
        switch (type) {
            case ZIP -> streamEntryFromZip(cbxPath, entryName, outputStream, metadata);
            case SEVEN_ZIP -> streamEntryFrom7z(cbxPath, entryName, outputStream);
            case RAR -> streamEntryFromRar(cbxPath, entryName, outputStream);
            default -> throw new IOException("Unsupported archive format: " + cbxPath.getFileName());
        }
    }

    private boolean streamEntryFromZipWithEncoding(Path cbxPath, String entryName, OutputStream outputStream, Charset charset, boolean useFastPath, boolean useUnicodeExtraFields) throws IOException {
        try (org.apache.commons.compress.archivers.zip.ZipFile zipFile =
                     org.apache.commons.compress.archivers.zip.ZipFile.builder()
                             .setPath(cbxPath)
                             .setCharset(charset)
                             .setUseUnicodeExtraFields(useUnicodeExtraFields)
                             .setIgnoreLocalFileHeader(useFastPath)
                             .get()) {
            ZipArchiveEntry entry = zipFile.getEntry(entryName);
            if (entry != null) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    IOUtils.copy(in, outputStream);
                }
                return true;
            }
        }
        return false;
    }

    private List<String> getImageEntriesFrom7z(Path cbxPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(cbxPath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (!entry.isDirectory() && isImageFile(entry.getName())) {
                    entries.add(entry.getName());
                }
            }
        }
        sortNaturally(entries);
        return entries;
    }

    private void streamEntryFrom7z(Path cbxPath, String entryName, OutputStream outputStream) throws IOException {
        try (SevenZFile sevenZFile = SevenZFile.builder().setPath(cbxPath).get()) {
            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    copySevenZEntry(sevenZFile, outputStream, entry.getSize());
                    return;
                }
            }
        }
        throw new FileNotFoundException("Entry not found in 7z archive: " + entryName);
    }

    private void copySevenZEntry(SevenZFile sevenZFile, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = sevenZFile.read(buffer, 0, toRead);
            if (read == -1) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private List<String> getImageEntriesFromRar(Path cbxPath) throws IOException {
        List<String> entries = new ArrayList<>();
        try (Archive archive = new Archive(cbxPath.toFile())) {
            for (FileHeader header : archive.getFileHeaders()) {
                if (!header.isDirectory() && isImageFile(header.getFileName())) {
                    entries.add(header.getFileName());
                }
            }
        } catch (Exception e) {
            if (UnrarHelper.isAvailable()) {
                log.info("junrar failed for {}, falling back to unrar CLI: {}", cbxPath.getFileName(), e.getMessage());
                entries = UnrarHelper.listEntries(cbxPath).stream()
                        .filter(this::isImageFile)
                        .collect(Collectors.toCollection(ArrayList::new));
            } else {
                throw new IOException("Failed to read RAR archive: " + e.getMessage(), e);
            }
        }
        sortNaturally(entries);
        return entries;
    }

    private void streamEntryFromRar(Path cbxPath, String entryName, OutputStream outputStream) throws IOException {
        try (Archive archive = new Archive(cbxPath.toFile())) {
            for (FileHeader header : archive.getFileHeaders()) {
                if (header.getFileName().equals(entryName)) {
                    archive.extractFile(header, outputStream);
                    return;
                }
            }
        } catch (Exception e) {
            if (UnrarHelper.isAvailable()) {
                log.info("junrar failed for {}, falling back to unrar CLI: {}", cbxPath.getFileName(), e.getMessage());
                UnrarHelper.extractEntry(cbxPath, entryName, outputStream);
                return;
            }
            throw new IOException("Failed to extract from RAR archive: " + e.getMessage(), e);
        }
        throw new FileNotFoundException("Entry not found in RAR archive: " + entryName);
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

    private void sortNaturally(List<String> entries) {
        entries.sort((s1, s2) -> {
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
        });
    }
}
