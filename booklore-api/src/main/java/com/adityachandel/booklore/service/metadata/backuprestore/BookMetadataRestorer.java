package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.repository.AuthorRepository;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.CategoryRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.MetadataMatchService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookMetadataRestorer {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final BookRepository bookRepository;
    private final MetadataMatchService metadataMatchService;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;

    @Transactional
    public void restoreMetadata(BookEntity bookEntity, BookMetadata backup, String coverPath) {
        BookMetadataEntity metadata = bookEntity.getMetadata();

        if (!isLocked(metadata.getTitleLocked())) metadata.setTitle(backup.getTitle());
        if (!isLocked(metadata.getSubtitleLocked())) metadata.setSubtitle(backup.getSubtitle());
        if (!isLocked(metadata.getPublisherLocked())) metadata.setPublisher(backup.getPublisher());
        if (!isLocked(metadata.getPublishedDateLocked())) metadata.setPublishedDate(backup.getPublishedDate());
        if (!isLocked(metadata.getDescriptionLocked())) metadata.setDescription(backup.getDescription());
        if (!isLocked(metadata.getLanguageLocked())) metadata.setLanguage(backup.getLanguage());
        if (!isLocked(metadata.getPageCountLocked())) metadata.setPageCount(backup.getPageCount());

        if (!isLocked(metadata.getSeriesNameLocked())) metadata.setSeriesName(backup.getSeriesName());
        if (!isLocked(metadata.getSeriesNumberLocked())) metadata.setSeriesNumber(backup.getSeriesNumber());
        if (!isLocked(metadata.getSeriesTotalLocked())) metadata.setSeriesTotal(backup.getSeriesTotal());

        if (!isLocked(metadata.getIsbn13Locked())) metadata.setIsbn13(backup.getIsbn13());
        if (!isLocked(metadata.getIsbn10Locked())) metadata.setIsbn10(backup.getIsbn10());
        if (!isLocked(metadata.getAsinLocked())) metadata.setAsin(backup.getAsin());
        if (!isLocked(metadata.getGoodreadsIdLocked())) metadata.setGoodreadsId(backup.getGoodreadsId());
        if (!isLocked(metadata.getComicvineIdLocked())) metadata.setComicvineId(backup.getComicvineId());
        if (!isLocked(metadata.getHardcoverIdLocked())) metadata.setHardcoverId(backup.getHardcoverId());
        if (!isLocked(metadata.getGoogleIdLocked())) metadata.setGoogleId(backup.getGoogleId());

        if (!isLocked(metadata.getAuthorsLocked())) {
            Set<AuthorEntity> authors = new HashSet<>();
            if (backup.getAuthors() != null) {
                authors = backup.getAuthors().stream()
                        .map(name -> authorRepository.findByName(name)
                                .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(name).build())))
                        .collect(Collectors.toSet());
            }
            metadata.setAuthors(authors);
        }

        if (!isLocked(metadata.getCategoriesLocked())) {
            Set<CategoryEntity> categories = new HashSet<>();
            if (backup.getCategories() != null) {
                categories = backup.getCategories().stream()
                        .map(name -> categoryRepository.findByName(name)
                                .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())))
                        .collect(Collectors.toSet());
            }
            metadata.setCategories(categories);
        }

        if (!isLocked(metadata.getPersonalRatingLocked())) metadata.setPersonalRating(backup.getPersonalRating());
        if (!isLocked(metadata.getAmazonRatingLocked())) metadata.setAmazonRating(backup.getAmazonRating());
        if (!isLocked(metadata.getAmazonReviewCountLocked())) metadata.setAmazonReviewCount(backup.getAmazonReviewCount());

        if (!isLocked(metadata.getGoodreadsRatingLocked())) metadata.setGoodreadsRating(backup.getGoodreadsRating());
        if (!isLocked(metadata.getGoodreadsReviewCountLocked())) metadata.setGoodreadsReviewCount(backup.getGoodreadsReviewCount());

        if (!isLocked(metadata.getHardcoverRatingLocked())) metadata.setHardcoverRating(backup.getHardcoverRating());
        if (!isLocked(metadata.getHardcoverReviewCountLocked())) metadata.setHardcoverReviewCount(backup.getHardcoverReviewCount());

        bookMetadataRepository.save(metadata);

        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
            bookRepository.save(bookEntity);
        } catch (Exception e) {
            log.warn("Failed to calculate/save metadata match score for book ID {}: {}", bookEntity.getId(), e.getMessage());
        }

        try {
            MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
            boolean saveToOriginal = settings.isSaveToOriginalFile();
            boolean convertCbrCb7ToCbz = settings.isConvertCbrCb7ToCbz();
            if (saveToOriginal && (bookEntity.getBookType() != BookFileType.CBX || convertCbrCb7ToCbz)) {
                metadataWriterFactory.getWriter(bookEntity.getBookType()).ifPresent(writer -> {
                    try {
                        File file = new File(bookEntity.getFullFilePath().toUri());
                        writer.writeMetadataToFile(file, metadata, coverPath, true, new MetadataClearFlags());
                        log.info("Embedded metadata written to file for book ID {}", bookEntity.getId());
                    } catch (Exception e) {
                        log.warn("Failed to write metadata to file for book ID {}: {}", bookEntity.getId(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Error during embedded metadata write: {}", e.getMessage());
        }

        log.info("Metadata fully restored from backup for book ID {}", bookEntity.getId());
    }

    private boolean isLocked(Boolean locked) {
        return locked != null && locked;
    }
}