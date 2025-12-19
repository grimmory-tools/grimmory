package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.adityachandel.booklore.util.FileService.truncate;

@Slf4j
@Service
public class PdfProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final PdfMetadataExtractor pdfMetadataExtractor;
    private final BookMetadataRepository bookMetadataRepository;

    public PdfProcessor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        BookMetadataRepository bookMetadataRepository,
                        MetadataMatchService metadataMatchService,
                        PdfMetadataExtractor pdfMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        this.pdfMetadataExtractor = pdfMetadataExtractor;
        this.bookMetadataRepository = bookMetadataRepository;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.PDF);
        if (generateCover(bookEntity)) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
        }
        extractAndSetMetadata(bookEntity);
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        try (PDDocument pdf = Loader.loadPDF(new File(FileUtils.getBookFullPath(bookEntity)))) {
            return generateCoverImageAndSave(bookEntity.getId(), pdf);
        } catch (OutOfMemoryError e) {
            // Note: Catching OOM is generally discouraged, but for batch processing
            // of potentially large/corrupted PDFs, we prefer graceful degradation
            // over crashing the entire service.
            log.error("Out of memory (heap space exhausted) while generating cover for '{}'. Skipping cover generation.", bookEntity.getFileName());
            System.gc(); // Hint to JVM to reclaim memory
            return false;
        } catch (NegativeArraySizeException e) {
            // This can appear on corrupted PDF, or PDF with such large images that the
            // initial memory buffer is already bigger than the entire JVM heap, therefore
            // it leads to NegativeArrayException (basically run out of memory, and overflows)
            log.warn("Corrupted PDF structure for '{}'. Skipping cover generation.", bookEntity.getFileName());
            return false;
        } catch (Exception e) {
            log.warn("Failed to generate cover for '{}': {}", bookEntity.getFileName(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.PDF);
    }

    private void extractAndSetMetadata(BookEntity bookEntity) {
        try {
            BookMetadata extracted = pdfMetadataExtractor.extractMetadata(new File(FileUtils.getBookFullPath(bookEntity)));

            if (StringUtils.isNotBlank(extracted.getTitle())) {
                bookEntity.getMetadata().setTitle(truncate(extracted.getTitle(), 1000));
            }
            if (StringUtils.isNotBlank(extracted.getSeriesName())) {
                bookEntity.getMetadata().setSeriesName(truncate(extracted.getSeriesName(), 1000));
            }
            if (extracted.getSeriesNumber() != null) {
                bookEntity.getMetadata().setSeriesNumber(extracted.getSeriesNumber());
            }
            if (extracted.getAuthors() != null) {
                bookCreatorService.addAuthorsToBook(extracted.getAuthors(), bookEntity);
            }
            if (StringUtils.isNotBlank(extracted.getPublisher())) {
                bookEntity.getMetadata().setPublisher(extracted.getPublisher());
            }
            if (StringUtils.isNotBlank(extracted.getDescription())) {
                bookEntity.getMetadata().setDescription(truncate(extracted.getDescription(), 5000));
            }
            if (extracted.getPublishedDate() != null) {
                bookEntity.getMetadata().setPublishedDate(extracted.getPublishedDate());
            }
            if (StringUtils.isNotBlank(extracted.getLanguage())) {
                bookEntity.getMetadata().setLanguage(extracted.getLanguage());
            }
            if (StringUtils.isNotBlank(extracted.getAsin())) {
                bookEntity.getMetadata().setAsin(extracted.getAsin());
            }
            if (StringUtils.isNotBlank(extracted.getGoogleId())) {
                bookEntity.getMetadata().setGoogleId(extracted.getGoogleId());
            }
            if (StringUtils.isNotBlank(extracted.getHardcoverId())) {
                bookEntity.getMetadata().setHardcoverId(extracted.getHardcoverId());
            }
            if (StringUtils.isNotBlank(extracted.getGoodreadsId())) {
                bookEntity.getMetadata().setGoodreadsId(extracted.getGoodreadsId());
            }
            if (StringUtils.isNotBlank(extracted.getComicvineId())){
                bookEntity.getMetadata().setComicvineId(extracted.getComicvineId());
            }
            if (StringUtils.isNotBlank(extracted.getIsbn10())) {
                bookEntity.getMetadata().setIsbn10(extracted.getIsbn10());
            }
            if (StringUtils.isNotBlank(extracted.getIsbn13())) {
                bookEntity.getMetadata().setIsbn13(extracted.getIsbn13());
            }
            if (extracted.getCategories() != null) {
                bookCreatorService.addCategoriesToBook(extracted.getCategories(), bookEntity);
            }

        } catch (Exception e) {
            log.warn("Failed to extract PDF metadata for '{}': {}", bookEntity.getFileName(), e.getMessage());
        }
    }

    private boolean generateCoverImageAndSave(Long bookId, PDDocument document) throws IOException {
        BufferedImage coverImage = null;
        try {
            coverImage = new PDFRenderer(document).renderImageWithDPI(0, 150, ImageType.RGB);
            return fileService.saveCoverImages(coverImage, bookId);
        } catch (OutOfMemoryError e) {
            log.error("Out of memory (heap space exhausted) while generating cover for bookId {}. Skipping cover generation.", bookId);
            System.gc(); // Hint to JVM to reclaim memory
            return false;
        } finally {
            if (coverImage != null) {
                coverImage.flush(); // Release native resources
            }
        }
    }
}