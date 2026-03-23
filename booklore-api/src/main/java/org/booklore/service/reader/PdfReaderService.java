package org.booklore.service.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.PdfBookInfo;
import org.booklore.model.dto.response.PdfOutlineItem;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReaderService {

    private static final int MAX_CACHE_ENTRIES = 50;
    private static final float DEFAULT_DPI = 200f;

    private final BookRepository bookRepository;
    private final Map<String, CachedPdfMetadata> metadataCache = new ConcurrentHashMap<>();

    private static class CachedPdfMetadata {
        final int pageCount;
        final long lastModified;
        final List<PdfOutlineItem> outline;
        volatile long lastAccessed;

        CachedPdfMetadata(int pageCount, long lastModified, List<PdfOutlineItem> outline) {
            this.pageCount = pageCount;
            this.lastModified = lastModified;
            this.outline = outline;
            this.lastAccessed = System.currentTimeMillis();
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
        String bookFullPath = FileUtils.getBookFullPath(bookEntity);
        return Path.of(bookFullPath);
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
        CachedPdfMetadata cached = metadataCache.get(cacheKey);
        if (cached != null && cached.lastModified == currentModified) {
            cached.lastAccessed = System.currentTimeMillis();
            log.debug("Cache hit for PDF: {}", pdfPath.getFileName());
            return cached;
        }
        log.debug("Cache miss for PDF: {}, scanning...", pdfPath.getFileName());
        CachedPdfMetadata newMetadata = scanPdfMetadata(pdfPath);
        metadataCache.put(cacheKey, newMetadata);
        evictOldestCacheEntries();
        return newMetadata;
    }

    private CachedPdfMetadata scanPdfMetadata(Path pdfPath) throws IOException {
        if (!Files.isReadable(pdfPath)) {
            throw new FileNotFoundException("PDF file is not readable: " + pdfPath);
        }
        long lastModified = Files.getLastModifiedTime(pdfPath).toMillis();
        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(pdfPath.toFile());
             PDDocument document = Loader.loadPDF(randomAccessRead)) {
            int pageCount = document.getNumberOfPages();
            List<PdfOutlineItem> outline = extractOutline(document);
            return new CachedPdfMetadata(pageCount, lastModified, outline);
        }
    }

    private List<PdfOutlineItem> extractOutline(PDDocument document) {
        List<PdfOutlineItem> outline = new ArrayList<>();
        try {
            PDDocumentOutline documentOutline = document.getDocumentCatalog().getDocumentOutline();
            if (documentOutline != null) {
                PDOutlineItem item = documentOutline.getFirstChild();
                while (item != null) {
                    PdfOutlineItem outlineItem = buildOutlineItem(document, item);
                    if (outlineItem != null) {
                        outline.add(outlineItem);
                    }
                    item = item.getNextSibling();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract PDF outline: {}", e.getMessage());
        }
        return outline;
    }

    private PdfOutlineItem buildOutlineItem(PDDocument document, PDOutlineItem item) {
        try {
            String title = item.getTitle();
            if (title == null || title.isBlank()) {
                return null;
            }

            Integer pageNumber = null;
            try {
                PDPage page = item.findDestinationPage(document);
                if (page != null) {
                    int pageIndex = document.getPages().indexOf(page);
                    if (pageIndex >= 0) {
                        pageNumber = pageIndex + 1;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get page for outline item '{}': {}", title, e.getMessage());
            }

            List<PdfOutlineItem> children = new ArrayList<>();
            PDOutlineItem child = item.getFirstChild();
            while (child != null) {
                PdfOutlineItem childItem = buildOutlineItem(document, child);
                if (childItem != null) {
                    children.add(childItem);
                }
                child = child.getNextSibling();
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
            log.debug("Evicted cache entry: {}", key);
        });
    }

    private void renderPageToStream(Path pdfPath, int page, OutputStream outputStream) throws IOException {
        try (RandomAccessReadBufferedFile randomAccessRead = new RandomAccessReadBufferedFile(pdfPath.toFile());
             PDDocument document = Loader.loadPDF(randomAccessRead)) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = null;
            try {
                image = renderer.renderImageWithDPI(page - 1, DEFAULT_DPI, ImageType.RGB);
                ImageIO.write(image, "JPEG", outputStream);
            } finally {
                if (image != null) {
                    image.flush();
                }
            }
        } catch (IOException e) {
            log.error("Failed to render PDF page {} from {}", page, pdfPath, e);
            throw e;
        }
    }
}
