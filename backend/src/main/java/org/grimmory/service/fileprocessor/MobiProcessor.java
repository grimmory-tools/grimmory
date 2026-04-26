package org.grimmory.service.fileprocessor;

import org.grimmory.mapper.BookMapper;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.settings.LibraryFile;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.repository.BookAdditionalFileRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.book.BookCreatorService;
import org.grimmory.service.metadata.MetadataMatchService;
import org.grimmory.service.metadata.extractor.MobiMetadataExtractor;
import org.grimmory.service.metadata.sidecar.SidecarMetadataWriter;
import org.grimmory.util.BookCoverUtils;
import org.grimmory.util.FileService;
import org.grimmory.util.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
public class MobiProcessor extends AbstractFileProcessor implements BookFileProcessor {

    private final MobiMetadataExtractor mobiMetadataExtractor;

    public MobiProcessor(BookRepository bookRepository,
                         BookAdditionalFileRepository bookAdditionalFileRepository,
                         BookCreatorService bookCreatorService,
                         BookMapper bookMapper,
                         FileService fileService,
                         MetadataMatchService metadataMatchService,
                         SidecarMetadataWriter sidecarMetadataWriter,
                         MobiMetadataExtractor mobiMetadataExtractor) {
        super(bookRepository, bookAdditionalFileRepository, bookCreatorService, bookMapper, fileService, metadataMatchService, sidecarMetadataWriter);
        this.mobiMetadataExtractor = mobiMetadataExtractor;
    }

    @Override
    public BookEntity processNewFile(LibraryFile libraryFile) {
        BookFileType fileType = determineFileType(libraryFile.getFileName());
        BookEntity bookEntity = bookCreatorService.createShellBook(libraryFile, fileType);
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
            File mobiFile = FileUtils.getBookFullPath(bookEntity, bookFile).toFile();
            byte[] coverData = mobiMetadataExtractor.extractCover(mobiFile);

            if (coverData == null || coverData.length == 0) {
                log.warn("No cover image found in MOBI '{}'", bookFile.getFileName());
                return false;
            }

            return saveCoverImage(coverData, bookEntity.getId());

        } catch (Exception e) {
            log.error("Error generating cover for MOBI '{}': {}", bookFile.getFileName(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<BookFileType> getSupportedTypes() {
        return List.of(BookFileType.MOBI);
    }

    private BookFileType determineFileType(String fileName) {
        String lowerCase = fileName.toLowerCase();
        return BookFileType.MOBI;
    }

    private void setBookMetadata(BookEntity bookEntity) {
        File bookFile = new File(bookEntity.getFullFilePath().toUri());
        BookMetadata mobiMetadata = mobiMetadataExtractor.extractMetadata(bookFile);
        if (mobiMetadata == null) return;

        BookMetadataEntity metadata = bookEntity.getMetadata();

        metadata.setTitle(truncate(mobiMetadata.getTitle(), 1000));
        metadata.setSubtitle(truncate(mobiMetadata.getSubtitle(), 1000));
        metadata.setDescription(truncate(mobiMetadata.getDescription(), 2000));
        metadata.setPublisher(truncate(mobiMetadata.getPublisher(), 1000));
        metadata.setPublishedDate(mobiMetadata.getPublishedDate());
        metadata.setSeriesName(truncate(mobiMetadata.getSeriesName(), 1000));
        metadata.setSeriesNumber(mobiMetadata.getSeriesNumber());
        metadata.setSeriesTotal(mobiMetadata.getSeriesTotal());
        metadata.setIsbn13(truncate(mobiMetadata.getIsbn13(), 13));
        metadata.setIsbn10(truncate(mobiMetadata.getIsbn10(), 10));
        metadata.setPageCount(mobiMetadata.getPageCount());

        String lang = mobiMetadata.getLanguage();
        metadata.setLanguage(truncate((lang == null || "UND".equalsIgnoreCase(lang)) ? "en" : lang, 10));

        metadata.setAsin(truncate(mobiMetadata.getAsin(), 10));
        metadata.setAmazonRating(mobiMetadata.getAmazonRating());
        metadata.setAmazonReviewCount(mobiMetadata.getAmazonReviewCount());
        metadata.setGoodreadsId(truncate(mobiMetadata.getGoodreadsId(), 100));
        metadata.setGoodreadsRating(mobiMetadata.getGoodreadsRating());
        metadata.setGoodreadsReviewCount(mobiMetadata.getGoodreadsReviewCount());
        metadata.setHardcoverId(truncate(mobiMetadata.getHardcoverId(), 100));
        metadata.setHardcoverRating(mobiMetadata.getHardcoverRating());
        metadata.setHardcoverReviewCount(mobiMetadata.getHardcoverReviewCount());
        metadata.setGoogleId(truncate(mobiMetadata.getGoogleId(), 100));
        metadata.setComicvineId(truncate(mobiMetadata.getComicvineId(), 100));
        metadata.setRanobedbId(truncate(mobiMetadata.getRanobedbId(), 100));
        metadata.setRanobedbRating(mobiMetadata.getRanobedbRating());

        bookCreatorService.addAuthorsToBook(mobiMetadata.getAuthors(), bookEntity);

        if (mobiMetadata.getCategories() != null) {
            Set<String> validSubjects = mobiMetadata.getCategories().stream()
                    .filter(s -> s != null && !s.isBlank() && s.length() <= 100 && !s.contains("\n") && !s.contains("\r") && !s.contains("  "))
                    .collect(Collectors.toSet());
            bookCreatorService.addCategoriesToBook(validSubjects, bookEntity);
        }
    }

    private boolean saveCoverImage(byte[] coverData, long bookId) throws Exception {
        BufferedImage originalImage = FileService.readImage(coverData);
        if (originalImage == null) {
            log.warn("Failed to decode cover image for MOBI");
            return false;
        }

        return fileService.saveCoverImages(originalImage, bookId);
    }
}

