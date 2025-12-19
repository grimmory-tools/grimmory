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
import com.adityachandel.booklore.service.metadata.extractor.Fb2MetadataExtractor;
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
public class Fb2Processor extends AbstractFileProcessor implements BookFileProcessor {

    private final Fb2MetadataExtractor fb2MetadataExtractor;
    private final BookMetadataRepository bookMetadataRepository;

    public Fb2Processor(BookRepository bookRepository,
                        BookAdditionalFileRepository bookAdditionalFileRepository,
                        BookCreatorService bookCreatorService,
                        BookMapper bookMapper,
                        FileService fileService,
                        BookMetadataRepository bookMetadataRepository,
                        MetadataMatchService metadataMatchService,
                        Fb2MetadataExtractor fb2MetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService);
        this.fb2MetadataExtractor = fb2MetadataExtractor;
        this.bookMetadataRepository = bookMetadataRepository;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.FB2);
        setBookMetadata(bookEntity);
        if (generateCover(bookEntity)) {
            FileService.setBookCoverPath(bookEntity.getMetadata());
        }
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        try {
            File fb2File = new File(FileUtils.getBookFullPath(bookEntity));
            byte[] coverData = fb2MetadataExtractor.extractCover(fb2File);

            if (coverData == null || coverData.length == 0) {
                log.warn("No cover image found in FB2 '{}'", bookEntity.getFileName());
                return false;
            }

            boolean saved = saveCoverImage(coverData, bookEntity.getId());
            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for FB2 '{}': {}", bookEntity.getFileName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.FB2);
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        BookMetadata fb2Metadata = fb2MetadataExtractor.extractMetadata(bookFile);
        if (fb2Metadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(fb2Metadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(fb2Metadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(fb2Metadata.getDescription(), 2000));
        metadata.setPublisher(truncate(fb2Metadata.getPublisher(), 1000));
        metadata.setPublishedDate(fb2Metadata.getPublishedDate());
        metadata.setSeriesName(truncate(fb2Metadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(fb2Metadata.getSeriesNumber());
        metadata.setSeriesTotal(fb2Metadata.getSeriesTotal());
        metadata.setIsbn13(truncate(fb2Metadata.getIsbn13(), 64));
        metadata.setIsbn10(truncate(fb2Metadata.getIsbn10(), 64));
        metadata.setPageCount(fb2Metadata.getPageCount());

        String lang = fb2Metadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 1000));

        metadata.setAsin(truncate(fb2Metadata.getAsin(), 20));
        metadata.setAmazonRating(fb2Metadata.getAmazonRating());
        metadata.setAmazonReviewCount(fb2Metadata.getAmazonReviewCount());
        metadata.setGoodreadsId(truncate(fb2Metadata.getGoodreadsId(), 100));
        metadata.setGoodreadsRating(fb2Metadata.getGoodreadsRating());
        metadata.setGoodreadsReviewCount(fb2Metadata.getGoodreadsReviewCount());
        metadata.setHardcoverId(truncate(fb2Metadata.getHardcoverId(), 100));
        metadata.setHardcoverRating(fb2Metadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(fb2Metadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(fb2Metadata.getGoogleId(), 100));
        metadata.setComicvineId(truncate(fb2Metadata.getComicvineId(), 100));

        bookCreatorService.addAuthorsToBook(fb2Metadata.getAuthors(), bookEntity);

        if (fb2Metadata.getCategories() != null) {
            Set<String> validSubjects = fb2Metadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }
    }

    private boolean saveCoverImage(byte[] coverData, long bookId) throws Exception {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(coverData));
        try {
            return fileService.saveCoverImages(originalImage, bookId);
        } finally {
            if (originalImage != null) {
                originalImage.flush(); // Release resources after processing
            }
        }
    }
}
