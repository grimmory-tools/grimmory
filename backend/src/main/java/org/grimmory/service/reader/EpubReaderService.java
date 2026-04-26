package org.grimmory.service.reader;

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
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpubReaderService {

    private static final String CONTAINER_PATH = "META-INF/container.xml";

    private static final int MAX_CACHE_ENTRIES = 50;

    private static final Map<String, String> CONTENT_TYPE_MAP = Map.ofEntries(
            Map.entry(".xhtml", "application/xhtml+xml"),
            Map.entry(".html", "application/xhtml+xml"),
            Map.entry(".htm", "application/xhtml+xml"),
            Map.entry(".css", "text/css"),
            Map.entry(".js", "application/javascript"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf"),
            Map.entry(".otf", "font/otf"),
            Map.entry(".eot", "application/vnd.ms-fontobject"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".ncx", "application/x-dtbncx+xml"),
            Map.entry(".smil", "application/smil+xml"),
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".m4a", "audio/mp4"),
            Map.entry(".m4b", "audio/mp4"),
            Map.entry(".aac", "audio/aac"),
            Map.entry(".wav", "audio/wav"),
            Map.entry(".flac", "audio/flac"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".webm", "video/webm"),
            Map.entry(".avif", "image/avif"),
            Map.entry(".opf", "application/oebps-package+xml")
    );

    private final BookRepository bookRepository;
    private final com.github.benmanes.caffeine.cache.Cache<String, CachedEpubMetadata> metadataCache = com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private record CachedEpubMetadata(EpubBookInfo bookInfo, long lastModified,
                                      Set<String> validPaths,
                                      Map<String, EpubManifestItem> manifestByHref) {
        CachedEpubMetadata(EpubBookInfo bookInfo, long lastModified) {
            this(bookInfo, lastModified, buildValidPaths(bookInfo), buildManifestByHref(bookInfo));
        }

        private static Set<String> buildValidPaths(EpubBookInfo bookInfo) {
            Set<String> paths = new HashSet<>(bookInfo.getManifest().size() + 2);
            paths.add(CONTAINER_PATH);
            if (bookInfo.getContainerPath() != null) {
                paths.add(bookInfo.getContainerPath());
            }
            for (EpubManifestItem item : bookInfo.getManifest()) {
                paths.add(item.getHref());
            }
            return Collections.unmodifiableSet(paths);
        }

        private static Map<String, EpubManifestItem> buildManifestByHref(EpubBookInfo bookInfo) {
            Map<String, EpubManifestItem> byHref = new HashMap<>(bookInfo.getManifest().size());
            for (EpubManifestItem item : bookInfo.getManifest()) {
                byHref.put(item.getHref(), item);
            }
            return Collections.unmodifiableMap(byHref);
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
            String normalizedPath = normalizePath(filePath, metadata.bookInfo.getRootPath());

            // O(1) lookup instead of O(n) stream filter
            EpubManifestItem item = metadata.manifestByHref.get(normalizedPath);
            return item != null ? item.getSize() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path getBookPath(Long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findByIdForStreaming(bookId)
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
        CachedEpubMetadata cached = metadataCache.getIfPresent(cacheKey);

        if (cached != null && cached.lastModified() == currentModified) {
            log.debug("Cache hit for EPUB: {}", epubPath.getFileName());
            return cached;
        }

        log.debug("Cache miss for EPUB: {}, parsing...", epubPath.getFileName());
        CachedEpubMetadata newMetadata = parseEpubMetadata(epubPath, currentModified);
        metadataCache.put(cacheKey, newMetadata);
        return newMetadata;
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
