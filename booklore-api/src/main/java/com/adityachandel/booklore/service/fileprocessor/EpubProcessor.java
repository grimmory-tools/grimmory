package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.book.BookCreatorService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.adityachandel.booklore.util.FileService.truncate;

@Slf4j
@Service
public class EpubProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final EpubMetadataExtractor epubMetadataExtractor;
    private final BookMetadataRepository bookMetadataRepository;

    public EpubProcessor(BookRepository bookRepository,
                         BookAdditionalFileRepository bookAdditionalFileRepository,
                         BookCreatorService bookCreatorService,
                         BookMapper bookMapper,
                         FileService fileService,
                         BookMetadataRepository bookMetadataRepository,
                         MetadataMatchService metadataMatchService,
                         EpubMetadataExtractor epubMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        this.epubMetadataExtractor = epubMetadataExtractor;
        this.bookMetadataRepository = bookMetadataRepository;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB);
        setBookMetadata(bookEntity);
        if (generateCover(bookEntity)) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        try {
            File epubFile = new File(FileUtils.getBookFullPath(bookEntity));
            byte[] coverData = epubMetadataExtractor.extractCover(epubFile);

            if (coverData == null) {
                log.warn("No cover image found in EPUB '{}'", bookEntity.getFileName());
                return false;
            }

            boolean saved;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = ImageIO.read(bais);
                if (originalImage == null) {
                    log.warn("Cover image found but could not be decoded (possibly SVG or unsupported format) in EPUB '{}'", bookEntity.getFileName());
                    return false;
                }
                saved = fileService.saveCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();
            }

            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for EPUB '{}': {}", bookEntity.getFileName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.EPUB);
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        BookMetadata epubMetadata = epubMetadataExtractor.extractMetadata(bookFile);
        if (epubMetadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(epubMetadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(epubMetadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(epubMetadata.getDescription(), 2000));
        metadata.setPublisher(truncate(epubMetadata.getPublisher(), 1000));
        metadata.setPublishedDate(epubMetadata.getPublishedDate());
        metadata.setSeriesName(truncate(epubMetadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(epubMetadata.getSeriesNumber());
        metadata.setSeriesTotal(epubMetadata.getSeriesTotal());
        metadata.setIsbn13(truncate(epubMetadata.getIsbn13(), 64));
        metadata.setIsbn10(truncate(epubMetadata.getIsbn10(), 64));
        metadata.setPageCount(epubMetadata.getPageCount());

        String lang = epubMetadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 1000));

        metadata.setAsin(truncate(epubMetadata.getAsin(), 20));
        metadata.setAmazonRating(epubMetadata.getAmazonRating());
        metadata.setAmazonReviewCount(epubMetadata.getAmazonReviewCount());
        metadata.setGoodreadsId(truncate(epubMetadata.getGoodreadsId(), 100));
        metadata.setGoodreadsRating(epubMetadata.getGoodreadsRating());
        metadata.setGoodreadsReviewCount(epubMetadata.getGoodreadsReviewCount());
        metadata.setHardcoverId(truncate(epubMetadata.getHardcoverId(), 100));
        metadata.setHardcoverRating(epubMetadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(epubMetadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(epubMetadata.getGoogleId(), 100));
        metadata.setComicvineId(truncate(epubMetadata.getComicvineId(), 100));

        bookCreatorService.addAuthorsToBook(epubMetadata.getAuthors(), bookEntity);

        if (epubMetadata.getCategories() != null) {
            Set<String> validSubjects = epubMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }
    }
}