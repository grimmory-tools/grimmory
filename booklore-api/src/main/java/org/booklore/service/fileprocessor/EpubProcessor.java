package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.EpubMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
public class EpubProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final EpubMetadataExtractor epubMetadataExtractor;

    public EpubProcessor(BookRepository bookRepository,
                         BookAdditionalFileRepository bookAdditionalFileRepository,
                         BookCreatorService bookCreatorService,
                         BookMapper bookMapper,
                         FileService fileService,
                         MetadataMatchService metadataMatchService,
                         SidecarMetadataWriter sidecarMetadataWriter,
                         EpubMetadataExtractor epubMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.epubMetadataExtractor = epubMetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, BookFileType.EPUB);
        setBookMetadata(bookEntity);
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
        return bookEntity;
    }

    @Override
    public boolean generateCover(BookEntity bookEntity) {
        return generateCover(bookEntity, bookEntity.getPrimaryBookFile());
    }

    @Override
    public boolean generateCover(BookEntity bookEntity, BookFileEntity bookFile) {
        try {
            File epubFile = new File(FileUtils.getBookFullPath(bookEntity, bookFile));
            byte[] coverData = epubMetadataExtractor.extractCover(epubFile);

            if (coverData == null) {
                log.warn("No cover image found in EPUB '{}'", bookFile.getFileName());
                return false;
            }

            boolean saved;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(coverData)) {
                BufferedImage originalImage = ImageIO.read(bais);
                if (originalImage == null) {
                    log.warn("Cover image found but could not be decoded (possibly SVG or unsupported format) in EPUB '{}'", bookFile.getFileName());
                    return false;
                }
                saved = fileService.saveCoverImages(originalImage, bookEntity.getId());
                originalImage.flush();
            }

            return saved;

        } catch (Exception e) {
            log.error("Error generating cover for EPUB '{}': {}", bookFile.getFileName(), e.getMessage(), e);
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
        metadata.setHardcoverBookId(epubMetadata.getHardcoverBookId());
        metadata.setHardcoverRating(epubMetadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(epubMetadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(epubMetadata.getGoogleId(), 100));
        metadata.setComicvineId(truncate(epubMetadata.getComicvineId(), 100));
        metadata.setLubimyczytacId(truncate(epubMetadata.getLubimyczytacId(), 100));
        metadata.setLubimyczytacRating(epubMetadata.getLubimyczytacRating());
        metadata.setRanobedbId(truncate(epubMetadata.getRanobedbId(), 100));
        metadata.setRanobedbRating(epubMetadata.getRanobedbRating());
        metadata.setAgeRating(epubMetadata.getAgeRating());
        metadata.setContentRating(truncate(epubMetadata.getContentRating(), 20));

        bookCreatorService.addAuthorsToBook(epubMetadata.getAuthors(), bookEntity);

        if (epubMetadata.getCategories() != null) {
            Set<String> validSubjects = epubMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }

        if (epubMetadata.getMoods() != null && !epubMetadata.getMoods().isEmpty()) {
            Set<String> validMoods = epubMetadata.getMoods().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 255)
                    .collect(Collectors.toSet());
            bookCreatorService.addMoodsToBook(validMoods, bookEntity);
        }

        if (epubMetadata.getTags() != null && !epubMetadata.getTags().isEmpty()) {
            Set<String> validTags = epubMetadata.getTags().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 255)
                    .collect(Collectors.toSet());
            bookCreatorService.addTagsToBook(validTags, bookEntity);
        }

        bookEntity.getBookFiles().stream()
            .filter(bf -> bf.getBookType() == BookFileType.EPUB && bf.isBook())
            .findFirst()
            .ifPresent(ent -> ent.setFixedLayout(Boolean.TRUE.equals(epubMetadata.getIsFixedLayout())));
    }
}
