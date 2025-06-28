package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookCreatorService;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class PdfProcessor implements FileProcessor {

    private final BookRepository bookRepository;
    private final BookCreatorService bookCreatorService;
    private final BookMapper bookMapper;
    private final FileProcessingUtils fileProcessingUtils;
    private final BookMetadataRepository bookMetadataRepository;
    private final MetadataMatchService metadataMatchService;
    private final PdfMetadataExtractor pdfMetadataExtractor;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Book processFile(LibraryFile libraryFile, boolean forceProcess) {
        File bookFile = new File(libraryFile.getFileName());
        String fileName = bookFile.getName();
        if (!forceProcess) {
            Optional<BookEntity> bookOptional = bookRepository.findBookByFileNameAndLibraryId(fileName, libraryFile.getLibraryEntity().getId());
            return bookOptional
                    .map(bookMapper::toBook)
                    .orElseGet(() -> processNewFile(libraryFile));
        } else {
            return processNewFile(libraryFile);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Book processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.PDF);
        if (generateCover(bookEntity)) {
            fileProcessingUtils.setBookCoverPath(bookEntity.getId(), bookEntity.getMetadata());
        }
        setMetadata(bookEntity);
        Float score = metadataMatchService.calculateMatchScore(bookEntity);
        bookEntity.setMetadataMatchScore(score);
        bookCreatorService.saveConnections(bookEntity);
        bookEntity = bookRepository.save(bookEntity);
        bookRepository.flush();
        return bookMapper.toBook(bookEntity);
    }

    public boolean generateCover(BookEntity bookEntity) {
        try (PDDocument pdf = Loader.loadPDF(new File(FileUtils.getBookFullPath(bookEntity)))) {
            boolean saved = generateCoverImageAndSave(bookEntity.getId(), pdf);
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
            bookMetadataRepository.save(bookEntity.getMetadata());
            return saved;
        } catch (Exception e) {
            log.error("Error generating cover for pdf file {}, error: {}", bookEntity.getFileName(), e.getMessage());
        }
        return false;
    }

    private void setMetadata(BookEntity bookEntity) {
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
            if (StringUtils.isNotBlank(extracted.getIsbn10())) {
                bookEntity.getMetadata().setIsbn10(extracted.getIsbn10());
            }
            if (StringUtils.isNotBlank(extracted.getIsbn13())) {
                bookEntity.getMetadata().setIsbn13(extracted.getIsbn13());
            }
            if (extracted.getPersonalRating() != null) {
                bookEntity.getMetadata().setPersonalRating(extracted.getPersonalRating());
            }
            if (extracted.getCategories() != null) {
                bookCreatorService.addCategoriesToBook(extracted.getCategories(), bookEntity);
            }
        } catch (Exception e) {
            log.error("Failed to extract advanced PDF metadata for {}: {}", bookEntity.getFileName(), e.getMessage(), e);
        }
    }

    private boolean generateCoverImageAndSave(Long bookId, PDDocument document) throws IOException {
        BufferedImage coverImage = new PDFRenderer(document).renderImageWithDPI(0, 300, ImageType.RGB);
        return fileProcessingUtils.saveCoverImage(coverImage, bookId);
    }

    private String truncate(String input, int maxLength) {
        if (input == null) return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}
