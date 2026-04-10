package org.booklore.service.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.EpubBookInfo;
import org.booklore.model.dto.response.EpubManifestItem;
import org.booklore.model.dto.response.EpubSpineItem;
import org.booklore.model.dto.response.EpubTocItem;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.grimmory.epub4j.domain.*;
import org.grimmory.epub4j.epub.CoverDetector;
import org.grimmory.epub4j.epub.EpubReader;
import org.grimmory.epub4j.native_parsing.NativeArchive;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpubReaderService {

    private static final String CONTAINER_PATH = "META-INF/container.xml";

    private static final int MAX_CACHE_ENTRIES = 50;

    private static final Map<String, String> CONTENT_TYPE_MAP = createContentTypeMap();

    private static Map<String, String> createContentTypeMap() {
        Map<String, String> map = new HashMap<>(32);
        map.put(".xhtml", "application/xhtml+xml");
        map.put(".html", "application/xhtml+xml");
        map.put(".htm", "application/xhtml+xml");
        map.put(".css", "text/css");
        map.put(".js", "application/javascript");
        map.put(".jpg", "image/jpeg");
        map.put(".jpeg", "image/jpeg");
        map.put(".png", "image/png");
        map.put(".gif", "image/gif");
        map.put(".svg", "image/svg+xml");
        map.put(".webp", "image/webp");
        map.put(".woff", "font/woff");
        map.put(".woff2", "font/woff2");
        map.put(".ttf", "font/ttf");
        map.put(".otf", "font/otf");
        map.put(".eot", "application/vnd.ms-fontobject");
        map.put(".xml", "application/xml");
        map.put(".ncx", "application/x-dtbncx+xml");
        map.put(".smil", "application/smil+xml");
        map.put(".mp3", "audio/mpeg");
        map.put(".mp4", "video/mp4");
        map.put(".m4a", "audio/mp4");
        map.put(".m4b", "audio/mp4");
        map.put(".aac", "audio/aac");
        map.put(".wav", "audio/wav");
        map.put(".flac", "audio/flac");
        map.put(".ogg", "audio/ogg");
        map.put(".webm", "video/webm");
        map.put(".avif", "image/avif");
        map.put(".opf", "application/oebps-package+xml");
        return Collections.unmodifiableMap(map);
    }

    private final BookRepository bookRepository;
    private final Map<String, CachedEpubMetadata> metadataCache = new ConcurrentHashMap<>();

    private static class CachedEpubMetadata {
        final EpubBookInfo bookInfo;
        final long lastModified;
        final Set<String> validPaths;
        final Map<String, EpubManifestItem> manifestByHref;
        volatile long lastAccessed;

        CachedEpubMetadata(EpubBookInfo bookInfo, long lastModified) {
            this.bookInfo = bookInfo;
            this.lastModified = lastModified;
            this.lastAccessed = System.currentTimeMillis();

            Set<String> paths = new HashSet<>(bookInfo.getManifest().size() + 2);
            paths.add(CONTAINER_PATH);
            if (bookInfo.getContainerPath() != null) {
                paths.add(bookInfo.getContainerPath());
            }

            Map<String, EpubManifestItem> byHref = new HashMap<>(bookInfo.getManifest().size());
            for (EpubManifestItem item : bookInfo.getManifest()) {
                paths.add(item.getHref());
                byHref.put(item.getHref(), item);
            }

            this.validPaths = Collections.unmodifiableSet(paths);
            this.manifestByHref = Collections.unmodifiableMap(byHref);
        }

        void touch() {
            this.lastAccessed = System.currentTimeMillis();
        }
    }

    public EpubBookInfo getBookInfo(Long bookId) {
        return getBookInfo(bookId, null);
    }

    public EpubBookInfo getBookInfo(Long bookId, String bookType) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            return metadata.bookInfo;
        } catch (IOException e) {
            log.error("Failed to read EPUB for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read EPUB: " + e.getMessage());
        }
    }

    public void streamFile(Long bookId, String filePath, OutputStream outputStream) throws IOException {
        streamFile(bookId, null, filePath, outputStream);
    }

    public void streamFile(Long bookId, String bookType, String filePath, OutputStream outputStream) throws IOException {
        Path epubPath = getBookPath(bookId, bookType);
        CachedEpubMetadata metadata = getCachedMetadata(epubPath);

        String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        String actualPath;
        if (CONTAINER_PATH.equals(cleanPath) || cleanPath.equals(metadata.bookInfo.getContainerPath())) {
            actualPath = cleanPath;
        } else {
            actualPath = normalizePath(filePath, metadata.bookInfo.getRootPath());
        }

        if (!isValidPath(actualPath, metadata)) {
            throw new FileNotFoundException("File not found in EPUB: " + filePath);
        }

        streamEntryFromZip(epubPath, actualPath, outputStream);
    }

    public String getContentType(Long bookId, String filePath) {
        return getContentType(bookId, null, filePath);
    }

    public String getContentType(Long bookId, String bookType, String filePath) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            metadata.touch();
            String normalizedPath = normalizePath(filePath, metadata.bookInfo.getRootPath());
            EpubManifestItem item = metadata.manifestByHref.get(normalizedPath);
            return item != null ? item.getMediaType() : guessContentType(filePath);
        } catch (IOException e) {
            return guessContentType(filePath);
        }
    }

    public long getFileSize(Long bookId, String filePath) {
        return getFileSize(bookId, null, filePath);
    }

    public long getFileSize(Long bookId, String bookType, String filePath) {
        Path epubPath = getBookPath(bookId, bookType);
        try {
            CachedEpubMetadata metadata = getCachedMetadata(epubPath);
            metadata.touch();
            String normalizedPath = normalizePath(filePath, metadata.bookInfo.getRootPath());

            // O(1) lookup instead of O(n) stream filter
            EpubManifestItem item = metadata.manifestByHref.get(normalizedPath);
            return item != null ? item.getSize() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path getBookPath(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
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

    private CachedEpubMetadata getCachedMetadata(Path epubPath) throws IOException {
        String cacheKey = epubPath.toString();
        long currentModified = Files.getLastModifiedTime(epubPath).toMillis();
        CachedEpubMetadata cached = metadataCache.get(cacheKey);

        if (cached != null && cached.lastModified == currentModified) {
            cached.lastAccessed = System.currentTimeMillis();
            log.debug("Cache hit for EPUB: {}", epubPath.getFileName());
            return cached;
        }

        log.debug("Cache miss for EPUB: {}, parsing...", epubPath.getFileName());
        CachedEpubMetadata newMetadata = parseEpubMetadata(epubPath, currentModified);
        metadataCache.put(cacheKey, newMetadata);
        evictOldestCacheEntries();
        return newMetadata;
    }

    private void evictOldestCacheEntries() {
        if (metadataCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        List<String> keysToRemove = metadataCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastAccessed))
                .limit(metadataCache.size() - MAX_CACHE_ENTRIES)
                .map(Map.Entry::getKey)
                .toList();
        keysToRemove.forEach(key -> {
            metadataCache.remove(key);
            log.debug("Evicted EPUB cache entry: {}", key);
        });
    }

    private CachedEpubMetadata parseEpubMetadata(Path epubPath, long lastModified) throws IOException {
        try {
            Book book = new EpubReader().readEpubLazy(epubPath, "UTF-8");
            EpubBookInfo bookInfo = mapBookToInfo(book);
            return new CachedEpubMetadata(bookInfo, lastModified);
        } catch (Exception e) {
            throw new IOException("Unable to parse EPUB", e);
        }
    }

    private EpubBookInfo mapBookToInfo(Book book) {
        Resource opfResource = book.getOpfResource();
        String opfPath = opfResource != null ? opfResource.getHref() : "";
        String rootPath = opfPath.contains("/") ? opfPath.substring(0, opfPath.lastIndexOf('/') + 1) : "";

        Resource coverResource = CoverDetector.detectCoverImage(book);
        String coverHref = coverResource != null ? rootPath + coverResource.getHref() : null;

        List<EpubManifestItem> manifest = mapManifest(book, rootPath);
        List<EpubSpineItem> spine = mapSpine(book, rootPath);
        Map<String, Object> metadata = mapMetadata(book);
        EpubTocItem toc = mapToc(book.getTableOfContents(), rootPath);
        String coverPath = coverHref;

        return EpubBookInfo.builder()
                .containerPath(opfPath)
                .rootPath(rootPath)
                .spine(spine)
                .manifest(manifest)
                .toc(toc)
                .metadata(metadata)
                .coverPath(coverPath)
                .build();
    }

    private List<EpubManifestItem> mapManifest(Book book, String rootPath) {
        List<EpubManifestItem> manifest = new ArrayList<>();
        for (Resource resource : book.getResources().getAll()) {
            String fullHref = rootPath + resource.getHref();

            // Use EPUB3 manifest item properties directly from the parsed resource
            List<String> properties = null;
            if (resource.getProperties() != null && !resource.getProperties().isEmpty()) {
                properties = resource.getProperties().stream()
                        .map(ManifestItemProperties::getName)
                        .toList();
            }

            manifest.add(EpubManifestItem.builder()
                    .id(resource.getId())
                    .href(fullHref)
                    .mediaType(resource.getMediaType().name())
                    .properties(properties)
                    .size(resource.getSize())
                    .build());
        }
        return manifest;
    }

    private List<EpubSpineItem> mapSpine(Book book, String rootPath) {
        List<EpubSpineItem> spine = new ArrayList<>();
        for (SpineReference ref : book.getSpine().getSpineReferences()) {
            Resource resource = ref.getResource();
            spine.add(EpubSpineItem.builder()
                    .idref(resource.getId())
                    .href(rootPath + resource.getHref())
                    .mediaType(resource.getMediaType().name())
                    .linear(ref.isLinear())
                    .build());
        }
        return spine;
    }

    private Map<String, Object> mapMetadata(Book book) {
        Map<String, Object> metadata = new HashMap<>();
        Metadata md = book.getMetadata();

        String title = md.getFirstTitle();
        if (title != null && !title.isEmpty()) metadata.put("title", title);

        List<Author> authors = md.getAuthors();
        if (authors != null && !authors.isEmpty()) {
            Author first = authors.get(0);
            String name = first.getFirstname();
            if (first.getLastname() != null && !first.getLastname().isEmpty()) {
                name = (name != null && !name.isEmpty()) ? name + " " + first.getLastname() : first.getLastname();
            }
            if (name != null && !name.isEmpty()) metadata.put("creator", name);
        }

        String language = md.getLanguage();
        if (language != null && !language.isEmpty()) metadata.put("language", language);

        List<String> publishers = md.getPublishers();
        if (publishers != null && !publishers.isEmpty()) metadata.put("publisher", publishers.get(0));

        List<Identifier> identifiers = md.getIdentifiers();
        if (identifiers != null && !identifiers.isEmpty()) metadata.put("identifier", identifiers.get(0).getValue());

        List<String> descriptions = md.getDescriptions();
        if (descriptions != null && !descriptions.isEmpty()) metadata.put("description", descriptions.get(0));

        // EPUB3 rendition properties
        if (md.getRenditionLayout() != null) metadata.put("rendition:layout", md.getRenditionLayout());
        if (md.getRenditionOrientation() != null) metadata.put("rendition:orientation", md.getRenditionOrientation());
        if (md.getRenditionSpread() != null) metadata.put("rendition:spread", md.getRenditionSpread());
        if (md.getMediaDuration() != null) metadata.put("media:duration", md.getMediaDuration());

        // Page progression direction from spine
        String ppd = book.getSpine().getPageProgressionDirection();
        if (ppd != null && !ppd.isEmpty()) metadata.put("page-progression-direction", ppd);

        return metadata;
    }

    private EpubTocItem mapToc(TableOfContents toc, String rootPath) {
        if (toc == null || toc.getTocReferences() == null || toc.getTocReferences().isEmpty()) {
            return null;
        }
        List<EpubTocItem> children = toc.getTocReferences().stream()
                .map(ref -> mapTocReference(ref, rootPath))
                .filter(Objects::nonNull)
                .toList();

        return EpubTocItem.builder()
                .label("Table of Contents")
                .children(children.isEmpty() ? null : children)
                .build();
    }

    private EpubTocItem mapTocReference(TOCReference tocRef, String rootPath) {
        String label = tocRef.getTitle();
        String href = tocRef.getResource() != null ? rootPath + tocRef.getResource().getHref() : null;
        if (href != null && tocRef.getFragmentId() != null && !tocRef.getFragmentId().isEmpty()) {
            href += "#" + tocRef.getFragmentId();
        }

        List<EpubTocItem> children = null;
        if (tocRef.getChildren() != null && !tocRef.getChildren().isEmpty()) {
            children = tocRef.getChildren().stream()
                    .map(ref -> mapTocReference(ref, rootPath))
                    .filter(Objects::nonNull)
                    .toList();
        }

        return EpubTocItem.builder()
                .label(label)
                .href(href)
                .children(children)
                .build();
    }

    private String normalizePath(String path, String rootPath) {
        if (path == null) return null;

        String normalized = path.startsWith("/") ? path.substring(1) : path;

        if (rootPath != null && !rootPath.isEmpty() && normalized.startsWith(rootPath)) {
            return normalized;
        }

        if (rootPath != null && !rootPath.isEmpty()) {
            return rootPath + normalized;
        }

        return normalized;
    }

    private boolean isValidPath(String path, CachedEpubMetadata metadata) {
        if (path == null) return false;
        if (path.contains("..")) return false;

        return metadata.validPaths.contains(path);
    }

    private void streamEntryFromZip(Path epubPath, String entryName, OutputStream outputStream) throws IOException {
        try (NativeArchive archive = NativeArchive.open(epubPath)) {
            archive.streamEntry(entryName, outputStream);
        }
    }

    private String guessContentType(String path) {
        if (path == null) return "application/octet-stream";

        int lastDot = path.lastIndexOf('.');
        if (lastDot < 0) return "application/octet-stream";

        String extension = path.substring(lastDot).toLowerCase();
        return CONTENT_TYPE_MAP.getOrDefault(extension, "application/octet-stream");
    }
}
