package org.booklore.service.fileprocessor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.PdfPage;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.PdfMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
public class PdfProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final PdfMetadataExtractor pdfMetadataExtractor;

    public PdfProcessor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        MetadataMatchService metadataMatchService,
                        SidecarMetadataWriter sidecarMetadataWriter,
                        PdfMetadataExtractor pdfMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.pdfMetadataExtractor = pdfMetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.PDF);
        boolean coverGenerated = generateCover(bookEntity);
        if (!coverGenerated) {
            var folder = getBookFolderForCoverFallback(libraryFile);
            if (folder != null) {
                coverGenerated = generateCoverFromFolderImage(bookEntity, folder);
            }
        }
        if (coverGenerated) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
        }
        extractAndSetMetadata(bookEntity);
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return generateCover(bookEntity, bookEntity.getPrimaryBookFile());
    }

    @Override
    public boolean generateCover(BookEntity bookEntity, BookFileEntity bookFile) {
        File pdfFile = FileUtils.getBookFullPath(bookEntity, bookFile).toFile();
        try (PdfDocument doc = PdfDocument.open(pdfFile.toPath())) {
            return generateCoverImageAndSave(bookEntity.getId(), doc);
        } catch (OutOfMemoryError e) {
            // Note: Catching OOM is generally discouraged, but for batch processing
            // of potentially large/corrupted PDFs, we prefer graceful degradation
            // over crashing the entire service.
            log.error("Out of memory (heap space exhausted) while generating cover for '{}'. Skipping cover generation.", bookFile.getFileName());
            System.gc(); // Hint to JVM to reclaim memory
            return false;
        } catch (NegativeArraySizeException e) {
            // This can appear on corrupted PDF, or PDF with such large images that the
            // initial memory buffer is already bigger than the entire JVM heap, therefore
            // it leads to NegativeArrayException (basically run out of memory, and overflows)
            log.warn("Corrupted PDF structure for '{}'. Skipping cover generation.", bookFile.getFileName());
            return false;
        } catch (Exception e) {
            log.warn("Failed to generate cover for '{}': {}", bookFile.getFileName(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.PDF);
    }

    private void extractAndSetMetadata(BookEntity bookEntity) {
        try {
            BookMetadata extracted = pdfMetadataExtractor.extractMetadata(FileUtils.getBookFullPath(bookEntity).toFile());

            if (StringUtils.isNotBlank(extracted.getTitle())) {
                bookEntity.getMetadata().setTitle(truncate(extracted.getTitle(), 1000));
            }
            if (StringUtils.isNotBlank(extracted.getSubtitle())) {
                bookEntity.getMetadata().setSubtitle(truncate(extracted.getSubtitle(), 1000));
            }
            if (StringUtils.isNotBlank(extracted.getSeriesName())) {
                bookEntity.getMetadata().setSeriesName(truncate(extracted.getSeriesName(), 1000));
            }
            if (extracted.getSeriesNumber() != null) {
                bookEntity.getMetadata().setSeriesNumber(extracted.getSeriesNumber());
            }
            if (extracted.getSeriesTotal() != null) {
                bookEntity.getMetadata().setSeriesTotal(extracted.getSeriesTotal());
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
                bookEntity.getMetadata().setLanguage(truncate(extracted.getLanguage(), 10));
            }
            if (extracted.getPageCount() != null) {
                bookEntity.getMetadata().setPageCount(extracted.getPageCount());
            }
            
            // External IDs
            if (StringUtils.isNotBlank(extracted.getAsin())) {
                bookEntity.getMetadata().setAsin(truncate(extracted.getAsin(), 10));
            }
            if (StringUtils.isNotBlank(extracted.getGoogleId())) {
                bookEntity.getMetadata().setGoogleId(extracted.getGoogleId());
            }
            if (StringUtils.isNotBlank(extracted.getHardcoverId())) {
                bookEntity.getMetadata().setHardcoverId(extracted.getHardcoverId());
            }
            if (StringUtils.isNotBlank(extracted.getHardcoverBookId())) {
                bookEntity.getMetadata().setHardcoverBookId(extracted.getHardcoverBookId());
            }
            if (StringUtils.isNotBlank(extracted.getGoodreadsId())) {
                bookEntity.getMetadata().setGoodreadsId(extracted.getGoodreadsId());
            }
            if (StringUtils.isNotBlank(extracted.getComicvineId())) {
                bookEntity.getMetadata().setComicvineId(extracted.getComicvineId());
            }
            if (StringUtils.isNotBlank(extracted.getRanobedbId())) {
                bookEntity.getMetadata().setRanobedbId(extracted.getRanobedbId());
            }
            if (StringUtils.isNotBlank(extracted.getLubimyczytacId())) {
                bookEntity.getMetadata().setLubimyczytacId(extracted.getLubimyczytacId());
            }
            if (StringUtils.isNotBlank(extracted.getIsbn10())) {
                bookEntity.getMetadata().setIsbn10(truncate(extracted.getIsbn10(), 10));
            }
            if (StringUtils.isNotBlank(extracted.getIsbn13())) {
                bookEntity.getMetadata().setIsbn13(truncate(extracted.getIsbn13(), 13));
            }
            
            // Categories, moods, and tags
            if (extracted.getCategories() != null && !extracted.getCategories().isEmpty()) {
                bookCreatorService.addCategoriesToBook(extracted.getCategories(), bookEntity);
            }
            if (extracted.getMoods() != null && !extracted.getMoods().isEmpty()) {
                bookCreatorService.addMoodsToBook(extracted.getMoods(), bookEntity);
            }
            if (extracted.getTags() != null && !extracted.getTags().isEmpty()) {
                bookCreatorService.addTagsToBook(extracted.getTags(), bookEntity);
            }
            
            // Ratings
            if (extracted.getAmazonRating() != null) {
                bookEntity.getMetadata().setAmazonRating(extracted.getAmazonRating());
            }
            if (extracted.getGoodreadsRating() != null) {
                bookEntity.getMetadata().setGoodreadsRating(extracted.getGoodreadsRating());
            }
            if (extracted.getHardcoverRating() != null) {
                bookEntity.getMetadata().setHardcoverRating(extracted.getHardcoverRating());
            }
            if (extracted.getLubimyczytacRating() != null) {
                bookEntity.getMetadata().setLubimyczytacRating(extracted.getLubimyczytacRating());
            }
            if (extracted.getRanobedbRating() != null) {
                bookEntity.getMetadata().setRanobedbRating(extracted.getRanobedbRating());
            }
            if (extracted.getRating() != null) {
                bookEntity.getMetadata().setRating(extracted.getRating());
            }

        } catch (Exception e) {
            log.warn("Failed to extract PDF metadata for '{}': {}", bookEntity.getPrimaryBookFile().getFileName(), e.getMessage());
        }
    }

    private boolean generateCoverImageAndSave(Long bookId, PdfDocument doc) throws IOException {
        BufferedImage coverImage = null;
        try (PdfPage page = doc.page(0)) {
            coverImage = page.render(150).toBufferedImage();
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
