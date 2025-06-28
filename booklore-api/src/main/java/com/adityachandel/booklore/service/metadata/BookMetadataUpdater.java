package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.MetadataUpdateWrapper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.AuthorRepository;
import com.adityachandel.booklore.repository.CategoryRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.metadata.backuprestore.MetadataBackupRestore;
import com.adityachandel.booklore.service.metadata.backuprestore.MetadataBackupRestoreFactory;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import com.adityachandel.booklore.util.FileService;
import com.adityachandel.booklore.util.MetadataChangeDetector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataUpdater {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final FileService fileService;
    private final MetadataMatchService metadataMatchService;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final MetadataBackupRestoreFactory metadataBackupRestoreFactory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setBookMetadata(BookEntity bookEntity, MetadataUpdateWrapper wrapper, boolean setThumbnail, boolean mergeCategories) {
        Long bookId = bookEntity.getId();
        BookMetadata newMetadata = wrapper.getMetadata();
        MetadataClearFlags clearFlags = wrapper.getClearFlags();
        BookMetadataEntity metadata = bookEntity.getMetadata();

        boolean thumbnailRequiresUpdate = StringUtils.hasText(newMetadata.getThumbnailUrl());
        boolean hasMetadataChanges = MetadataChangeDetector.isDifferent(newMetadata, metadata, clearFlags);
        if (!thumbnailRequiresUpdate && !hasMetadataChanges) {
            log.info("No changes in metadata for book ID {}. Skipping update.", bookId);
            return;
        }

        updateLocks(newMetadata, metadata);

        if (metadata.areAllFieldsLocked()) {
            log.warn("All fields are locked for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean writeToFile = settings.isSaveToOriginalFile();
        boolean backupEnabled = settings.isBackupMetadata();
        boolean backupCover = settings.isBackupCover();
        BookFileType bookType = bookEntity.getBookType();

        if (writeToFile && backupEnabled) {
            try {
                MetadataBackupRestore service = metadataBackupRestoreFactory.getService(bookType);
                if (service != null) {
                    boolean coverBackup = bookType == BookFileType.EPUB && backupCover;
                    service.backupEmbeddedMetadataIfNotExists(bookEntity, coverBackup);
                }
            } catch (Exception e) {
                log.warn("Metadata backup failed for book ID {}: {}", bookId, e.getMessage());
            }
        }

        updateBasicFields(newMetadata, metadata, clearFlags);
        updateAuthorsIfNeeded(newMetadata, metadata, clearFlags);
        updateCategoriesIfNeeded(newMetadata, metadata, clearFlags, mergeCategories);
        updateThumbnailIfNeeded(bookId, newMetadata, metadata, setThumbnail);

        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
        } catch (Exception e) {
            log.warn("Failed to calculate metadata match score for book ID {}: {}", bookId, e.getMessage());
        }

        if (writeToFile) {
            metadataWriterFactory.getWriter(bookType).ifPresent(writer -> {
                try {
                    String thumbnailUrl = setThumbnail ? newMetadata.getThumbnailUrl() : null;

                    if (StringUtils.hasText(thumbnailUrl) && isLocalOrPrivateUrl(thumbnailUrl)) {
                        log.warn("Blocked local/private thumbnail URL: {}", thumbnailUrl);
                        thumbnailUrl = null;
                    }

                    File file = new File(bookEntity.getFullFilePath().toUri());
                    writer.writeMetadataToFile(file, metadata, thumbnailUrl, false, clearFlags);
                    log.info("Metadata written for book ID {}", bookId);

                } catch (Exception e) {
                    log.warn("Failed to write metadata for book ID {}: {}", bookId, e.getMessage());
                }
            });
        }
    }

    private void updateBasicFields(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear) {
        handleFieldUpdate(e.getTitleLocked(), clear.isTitle(), m.getTitle(), v -> e.setTitle(nullIfBlank(v)));
        handleFieldUpdate(e.getSubtitleLocked(), clear.isSubtitle(), m.getSubtitle(), v -> e.setSubtitle(nullIfBlank(v)));
        handleFieldUpdate(e.getPublisherLocked(), clear.isPublisher(), m.getPublisher(), v -> e.setPublisher(nullIfBlank(v)));
        handleFieldUpdate(e.getPublishedDateLocked(), clear.isPublishedDate(), m.getPublishedDate(), e::setPublishedDate);
        handleFieldUpdate(e.getDescriptionLocked(), clear.isDescription(), m.getDescription(), v -> e.setDescription(nullIfBlank(v)));
        handleFieldUpdate(e.getSeriesNameLocked(), clear.isSeriesName(), m.getSeriesName(), e::setSeriesName);
        handleFieldUpdate(e.getSeriesNumberLocked(), clear.isSeriesNumber(), m.getSeriesNumber(), e::setSeriesNumber);
        handleFieldUpdate(e.getSeriesTotalLocked(), clear.isSeriesTotal(), m.getSeriesTotal(), e::setSeriesTotal);
        handleFieldUpdate(e.getIsbn13Locked(), clear.isIsbn13(), m.getIsbn13(), v -> e.setIsbn13(nullIfBlank(v)));
        handleFieldUpdate(e.getIsbn10Locked(), clear.isIsbn10(), m.getIsbn10(), v -> e.setIsbn10(nullIfBlank(v)));
        handleFieldUpdate(e.getAsinLocked(), clear.isAsin(), m.getAsin(), v -> e.setAsin(nullIfBlank(v)));
        handleFieldUpdate(e.getGoodreadsIdLocked(), clear.isGoodreadsId(), m.getGoodreadsId(), v -> e.setGoodreadsId(nullIfBlank(v)));
        handleFieldUpdate(e.getHardcoverIdLocked(), clear.isHardcoverId(), m.getHardcoverId(), v -> e.setHardcoverId(nullIfBlank(v)));
        handleFieldUpdate(e.getGoogleIdLocked(), clear.isGoogleId(), m.getGoogleId(), v -> e.setGoogleId(nullIfBlank(v)));
        handleFieldUpdate(e.getPageCountLocked(), clear.isPageCount(), m.getPageCount(), e::setPageCount);
        handleFieldUpdate(e.getLanguageLocked(), clear.isLanguage(), m.getLanguage(), v -> e.setLanguage(nullIfBlank(v)));
        handleFieldUpdate(e.getPersonalRatingLocked(), clear.isPersonalRating(), m.getPersonalRating(), e::setPersonalRating);
        handleFieldUpdate(e.getAmazonRatingLocked(), clear.isAmazonRating(), m.getAmazonRating(), e::setAmazonRating);
        handleFieldUpdate(e.getAmazonReviewCountLocked(), clear.isAmazonReviewCount(), m.getAmazonReviewCount(), e::setAmazonReviewCount);
        handleFieldUpdate(e.getGoodreadsRatingLocked(), clear.isGoodreadsRating(), m.getGoodreadsRating(), e::setGoodreadsRating);
        handleFieldUpdate(e.getGoodreadsReviewCountLocked(), clear.isGoodreadsReviewCount(), m.getGoodreadsReviewCount(), e::setGoodreadsReviewCount);
        handleFieldUpdate(e.getHardcoverRatingLocked(), clear.isHardcoverRating(), m.getHardcoverRating(), e::setHardcoverRating);
        handleFieldUpdate(e.getHardcoverReviewCountLocked(), clear.isHardcoverReviewCount(), m.getHardcoverReviewCount(), e::setHardcoverReviewCount);
    }

    private <T> void handleFieldUpdate(Boolean locked, boolean shouldClear, T newValue, Consumer<T> setter) {
        if (Boolean.TRUE.equals(locked)) return;
        if (shouldClear) setter.accept(null);
        else if (newValue != null) setter.accept(newValue);
    }

    private void updateAuthorsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear) {
        if (Boolean.TRUE.equals(e.getAuthorsLocked())) {
            // Locked — do nothing
        } else if (clear.isAuthors()) {
            e.setAuthors(Set.of());
        } else if (shouldUpdateField(false, m.getAuthors()) && m.getAuthors() != null) {
            e.setAuthors(m.getAuthors().stream()
                    .filter(a -> a != null && !a.isBlank())
                    .map(name -> authorRepository.findByName(name)
                            .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(name).build())))
                    .collect(Collectors.toSet()));
        }
    }

    private void updateCategoriesIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge) {
        if (Boolean.TRUE.equals(e.getCategoriesLocked())) {
            // Locked — do nothing
        } else if (clear.isCategories()) {
            e.setCategories(Set.of());
        } else if (shouldUpdateField(false, m.getCategories()) && m.getCategories() != null) {
            if (merge) {
                Set<CategoryEntity> existing = new HashSet<>(e.getCategories());
                for (String name : m.getCategories()) {
                    if (name == null || name.isBlank()) continue;
                    CategoryEntity entity = categoryRepository.findByName(name)
                            .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build()));
                    existing.add(entity);
                }
                e.setCategories(existing);
            } else {
                Set<CategoryEntity> result = m.getCategories().stream()
                        .filter(n -> n != null && !n.isBlank())
                        .map(name -> categoryRepository.findByName(name)
                                .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())))
                        .collect(Collectors.toSet());
                e.setCategories(result);
            }
        }
    }

    private void updateThumbnailIfNeeded(long bookId, BookMetadata m, BookMetadataEntity e, boolean set) {
        if (Boolean.TRUE.equals(e.getCoverLocked())) {
            return; // Locked — do nothing
        }
        if (!set) return;
        if (!StringUtils.hasText(m.getThumbnailUrl()) || isLocalOrPrivateUrl(m.getThumbnailUrl())) return;

        try {
            String path = fileService.createThumbnail(bookId, m.getThumbnailUrl());
            e.setThumbnail(path);
            e.setCoverUpdatedOn(Instant.now());
        } catch (IOException ex) {
            log.warn("Thumbnail generation failed for book {}: {}", bookId, ex.getMessage());
        }
    }

    private void updateLocks(BookMetadata m, BookMetadataEntity e) {
        List<Pair<Boolean, Consumer<Boolean>>> lockMappings = List.of(
                Pair.of(m.getTitleLocked(), e::setTitleLocked),
                Pair.of(m.getSubtitleLocked(), e::setSubtitleLocked),
                Pair.of(m.getPublisherLocked(), e::setPublisherLocked),
                Pair.of(m.getPublishedDateLocked(), e::setPublishedDateLocked),
                Pair.of(m.getDescriptionLocked(), e::setDescriptionLocked),
                Pair.of(m.getSeriesNameLocked(), e::setSeriesNameLocked),
                Pair.of(m.getSeriesNumberLocked(), e::setSeriesNumberLocked),
                Pair.of(m.getSeriesTotalLocked(), e::setSeriesTotalLocked),
                Pair.of(m.getIsbn13Locked(), e::setIsbn13Locked),
                Pair.of(m.getIsbn10Locked(), e::setIsbn10Locked),
                Pair.of(m.getAsinLocked(), e::setAsinLocked),
                Pair.of(m.getGoodreadsIdLocked(), e::setGoodreadsIdLocked),
                Pair.of(m.getHardcoverIdLocked(), e::setHardcoverIdLocked),
                Pair.of(m.getGoogleIdLocked(), e::setGoogleIdLocked),
                Pair.of(m.getPageCountLocked(), e::setPageCountLocked),
                Pair.of(m.getLanguageLocked(), e::setLanguageLocked),
                Pair.of(m.getPersonalRatingLocked(), e::setPersonalRatingLocked),
                Pair.of(m.getAmazonRatingLocked(), e::setAmazonRatingLocked),
                Pair.of(m.getAmazonReviewCountLocked(), e::setAmazonReviewCountLocked),
                Pair.of(m.getGoodreadsRatingLocked(), e::setGoodreadsRatingLocked),
                Pair.of(m.getGoodreadsReviewCountLocked(), e::setGoodreadsReviewCountLocked),
                Pair.of(m.getHardcoverRatingLocked(), e::setHardcoverRatingLocked),
                Pair.of(m.getHardcoverReviewCountLocked(), e::setHardcoverReviewCountLocked),
                Pair.of(m.getCoverLocked(), e::setCoverLocked),
                Pair.of(m.getAuthorsLocked(), e::setAuthorsLocked),
                Pair.of(m.getCategoriesLocked(), e::setCategoriesLocked)
        );
        lockMappings.forEach(pair -> {
            if (pair.getLeft() != null) pair.getRight().accept(pair.getLeft());
        });
    }

    private <T> void updateFieldIfUnlocked(Supplier<Boolean> lock, T value, Consumer<T> setter) {
        if ((lock.get() == null || !lock.get()) && value != null) {
            setter.accept(value);
        }
    }

    private boolean shouldUpdateField(Boolean locked, Object value) {
        return (locked == null || !locked) && value != null;
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean isLocalOrPrivateUrl(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            log.warn("Invalid thumbnail URL '{}': {}", url, e.getMessage());
            return true;
        }
    }
}