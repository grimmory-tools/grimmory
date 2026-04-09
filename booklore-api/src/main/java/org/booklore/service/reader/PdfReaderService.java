package org.booklore.service.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.model.Bookmark;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.PdfBookInfo;
import org.booklore.model.dto.response.PdfOutlineItem;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReaderService {

    private static final int MAX_CACHE_ENTRIES = 15;
    private static final float DEFAULT_DPI = 200f;

    private final BookRepository bookRepository;
    private final Cache<String, CachedPdfMetadata> metadataCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

    private static class CachedPdfMetadata {
        final int pageCount;
        final long lastModified;
        final List<PdfOutlineItem> outline;

        CachedPdfMetadata(int pageCount, long lastModified, List<PdfOutlineItem> outline) {
            this.pageCount = pageCount;
            this.lastModified = lastModified;
            this.outline = outline;
        }
    }

    public void getAvailablePages(Long bookId) {
        getAvailablePages(bookId, null);
    }

    public List<Integer> getAvailablePages(Long bookId, String bookType) {
        Path pdfPath = getBookPath(bookId, bookType);
        try {
            CachedPdfMetadata metadata = getCachedMetadata(pdfPath);
            return IntStream.rangeClosed(1, metadata.pageCount)
                    .boxed()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to read PDF for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read PDF: " + e.getMessage());
        }
    }

    public PdfBookInfo getBookInfo(Long bookId, String bookType) {
        Path pdfPath = getBookPath(bookId, bookType);
        try {
            CachedPdfMetadata metadata = getCachedMetadata(pdfPath);
            return PdfBookInfo.builder()
                    .pageCount(metadata.pageCount)
                    .outline(metadata.outline)
                    .build();
        } catch (IOException e) {
            log.error("Failed to read PDF for book {}", bookId, e);
            throw ApiError.FILE_READ_ERROR.createException("Failed to read PDF: " + e.getMessage());
        }
    }

    public void streamPageImage(Long bookId, int page, OutputStream outputStream) throws IOException {
        streamPageImage(bookId, null, page, outputStream);
    }

    public void streamPageImage(Long bookId, String bookType, int page, OutputStream outputStream) throws IOException {
        Path pdfPath = getBookPath(bookId, bookType);
        CachedPdfMetadata metadata = getCachedMetadata(pdfPath);
        validatePageRequest(bookId, page, metadata.pageCount);
        renderPageToStream(pdfPath, page, outputStream);
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

    private void validatePageRequest(Long bookId, int page, int pageCount) throws FileNotFoundException {
        if (pageCount == 0) {
            throw new FileNotFoundException("No pages found for book: " + bookId);
        }
        if (page < 1 || page > pageCount) {
            throw new FileNotFoundException("Page " + page + " out of range [1-" + pageCount + "]");
        }
    }

    private CachedPdfMetadata getCachedMetadata(Path pdfPath) throws IOException {
        String cacheKey = pdfPath.toString();
        long currentModified = Files.getLastModifiedTime(pdfPath).toMillis();
        CachedPdfMetadata cached = metadataCache.getIfPresent(cacheKey);
        if (cached != null && cached.lastModified == currentModified) {
            log.debug("Cache hit for PDF: {}", pdfPath.getFileName());
            return cached;
        }
        log.debug("Cache miss for PDF: {}, scanning...", pdfPath.getFileName());
        CachedPdfMetadata newMetadata = scanPdfMetadata(pdfPath);
        metadataCache.put(cacheKey, newMetadata);
        return newMetadata;
    }

    private CachedPdfMetadata scanPdfMetadata(Path pdfPath) throws IOException {
        if (!Files.isReadable(pdfPath)) {
            throw new FileNotFoundException("PDF file is not readable: " + pdfPath);
        }
        long lastModified = Files.getLastModifiedTime(pdfPath).toMillis();
        try (PdfDocument doc = PdfDocument.open(pdfPath)) {
            int pageCount = doc.pageCount();
            List<PdfOutlineItem> outline = extractOutline(doc);
            return new CachedPdfMetadata(pageCount, lastModified, outline);
        }
    }

    private List<PdfOutlineItem> extractOutline(PdfDocument doc) {
        List<PdfOutlineItem> outline = new ArrayList<>();
        try {
            List<Bookmark> bookmarks = doc.bookmarks();
            for (Bookmark bookmark : bookmarks) {
                PdfOutlineItem outlineItem = convertBookmark(bookmark);
                if (outlineItem != null) {
                    outline.add(outlineItem);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract PDF outline: {}", e.getMessage());
        }
        return outline;
    }

    private PdfOutlineItem convertBookmark(Bookmark bookmark) {
        try {
            String title = bookmark.title();
            if (title == null || title.isBlank()) {
                return null;
            }

            // PDFium4j pageIndex is 0-based, PdfOutlineItem.pageNumber is 1-based
            Integer pageNumber = bookmark.isInternal() ? bookmark.pageIndex() + 1 : null;

            List<PdfOutlineItem> children = new ArrayList<>();
            for (Bookmark child : bookmark.children()) {
                PdfOutlineItem childItem = convertBookmark(child);
                if (childItem != null) {
                    children.add(childItem);
                }
            }

            return PdfOutlineItem.builder()
                    .title(title.trim())
                    .pageNumber(pageNumber)
                    .children(children.isEmpty() ? null : children)
                    .build();
        } catch (Exception e) {
            log.debug("Failed to process outline item: {}", e.getMessage());
            return null;
        }
    }

    private void renderPageToStream(Path pdfPath, int page, OutputStream outputStream) throws IOException {
        try (PdfDocument doc = PdfDocument.open(pdfPath)) {
            // page is 1-based from the API, renderPageToBytes expects 0-based
            byte[] jpeg = doc.renderPageToBytes(page - 1, (int) DEFAULT_DPI, "jpeg");
            outputStream.write(jpeg);
        } catch (IOException e) {
            log.error("Failed to render PDF page {} from {}", page, pdfPath, e);
            throw e;
        }
    }
}
