package org.booklore.service.metadata;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.*;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.metadata.writer.MetadataWriterFactory;
import org.booklore.util.BookCoverUtils;
import org.booklore.util.FileService;
import org.booklore.util.MetadataChangeDetector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookMetadataUpdater {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final TagRepository tagRepository;
    private final BookRepository bookRepository;
    private final ComicMetadataRepository comicMetadataRepository;
    private final ComicCharacterRepository comicCharacterRepository;
    private final ComicTeamRepository comicTeamRepository;
    private final ComicLocationRepository comicLocationRepository;
    private final ComicCreatorRepository comicCreatorRepository;
    private final FileService fileService;
    private final MetadataMatchService metadataMatchService;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final BookReviewUpdateService bookReviewUpdateService;
    private final FileMoveService fileMoveService;

    @Transactional
    public void setBookMetadata(MetadataUpdateContext context) {
        BookEntity bookEntity = context.getBookEntity();
        MetadataUpdateWrapper wrapper = context.getMetadataUpdateWrapper();
        boolean mergeCategories = context.isMergeCategories();
        boolean mergeMoods = context.isMergeMoods();
        boolean mergeTags = context.isMergeTags();
        boolean updateThumbnail = context.isUpdateThumbnail();
        MetadataReplaceMode replaceMode = context.getReplaceMode();

        Long bookId = bookEntity.getId();
        BookMetadata newMetadata = wrapper.getMetadata();

        if (newMetadata == null) {
            log.warn("Metadata is null for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataClearFlags clearFlags = wrapper.getClearFlags();
        BookMetadataEntity metadata = bookEntity.getMetadata();

        boolean thumbnailRequiresUpdate = StringUtils.hasText(newMetadata.getThumbnailUrl());
        boolean hasMetadataChanges = MetadataChangeDetector.isDifferent(newMetadata, metadata, clearFlags);
        boolean hasValueChanges = MetadataChangeDetector.hasValueChanges(newMetadata, metadata, clearFlags);
        if (!thumbnailRequiresUpdate && !hasMetadataChanges) {
            log.info("No changes in metadata for book ID {}. Skipping update.", bookId);
            return;
        }

        if (metadata.areAllFieldsLocked() && hasValueChanges) {
            log.warn("All fields are locked for book ID {}. Skipping update.", bookId);
            return;
        }

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile writeToFile = settings.getSaveToOriginalFile();
        var primaryFile = bookEntity.getPrimaryBookFile();
        BookFileType bookType = primaryFile != null ? primaryFile.getBookType() : null;

        boolean hasValueChangesForFileWrite = MetadataChangeDetector.hasValueChangesForFileWrite(newMetadata, metadata, clearFlags);

        updateBasicFields(newMetadata, metadata, clearFlags, replaceMode);
        updateAuthorsIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateCategoriesIfNeeded(newMetadata, metadata, clearFlags, mergeCategories, replaceMode);
        updateMoodsIfNeeded(newMetadata, metadata, clearFlags, mergeMoods, replaceMode);
        updateTagsIfNeeded(newMetadata, metadata, clearFlags, mergeTags, replaceMode);
        bookReviewUpdateService.updateBookReviews(newMetadata, metadata, clearFlags, mergeCategories);
        updateThumbnailIfNeeded(bookId, bookEntity, newMetadata, metadata, updateThumbnail);
        updateAudiobookMetadataIfNeeded(bookEntity, newMetadata, metadata, clearFlags, replaceMode);
        updateComicMetadataIfNeeded(newMetadata, metadata, replaceMode);
        updateLocks(newMetadata, metadata);

        bookEntity.setMetadataUpdatedAt(Instant.now());
        bookRepository.save(bookEntity);
        try {
            Float score = metadataMatchService.calculateMatchScore(bookEntity);
            bookEntity.setMetadataMatchScore(score);
        } catch (Exception e) {
            log.warn("Failed to calculate metadata match score for book ID {}: {}", bookId, e.getMessage());
        }

        if (primaryFile != null && bookType != null && ((writeToFile.isAnyFormatEnabled() && hasValueChangesForFileWrite) || thumbnailRequiresUpdate)) {
            metadataWriterFactory.getWriter(bookType).ifPresent(writer -> {
                try {
                    String thumbnailUrl = updateThumbnail ? newMetadata.getThumbnailUrl() : null;
                    if ((StringUtils.hasText(thumbnailUrl) && isLocalOrPrivateUrl(thumbnailUrl) || Boolean.TRUE.equals(metadata.getCoverLocked()))) {
                        log.debug("Blocked local/private thumbnail URL: {}", thumbnailUrl);
                        thumbnailUrl = null;
                    }
                    File file = new File(bookEntity.getFullFilePath().toUri());
                    writer.saveMetadataToFile(file, metadata, thumbnailUrl, clearFlags);
                    String newHash = FileFingerprint.generateHash(bookEntity.getFullFilePath());
                    bookEntity.setMetadataForWriteUpdatedAt(Instant.now());
                    primaryFile.setCurrentHash(newHash);
                    bookRepository.save(bookEntity);
                } catch (Exception e) {
                    log.warn("Failed to write metadata for book ID {}: {}", bookId, e.getMessage());
                }
            });
        }

        boolean moveFilesToLibraryPattern = settings.isMoveFilesToLibraryPattern();
        if (moveFilesToLibraryPattern && primaryFile != null) {
            try {
                BookEntity book = metadata.getBook();
                FileMoveResult result = fileMoveService.moveSingleFile(book);
                if (result.isMoved()) {
                    var bookPrimaryFile = book.getPrimaryBookFile();
                    if (bookPrimaryFile != null) {
                        bookPrimaryFile.setFileName(result.getNewFileName());
                        bookPrimaryFile.setFileSubPath(result.getNewFileSubPath());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to move files for book ID {} after metadata update: {}", bookId, e.getMessage());
            }
        }
    }

    private void updateBasicFields(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        if (clear == null) {
            clear = new MetadataClearFlags();
        }
        handleFieldUpdate(e.getTitleLocked(), clear.isTitle(), m.getTitle(), v -> e.setTitle(nullIfBlank(v)), e::getTitle, replaceMode);
        handleFieldUpdate(e.getSubtitleLocked(), clear.isSubtitle(), m.getSubtitle(), v -> e.setSubtitle(nullIfBlank(v)), e::getSubtitle, replaceMode);
        handleFieldUpdate(e.getPublisherLocked(), clear.isPublisher(), m.getPublisher(), v -> e.setPublisher(nullIfBlank(v)), e::getPublisher, replaceMode);
        handleFieldUpdate(e.getPublishedDateLocked(), clear.isPublishedDate(), m.getPublishedDate(), e::setPublishedDate, e::getPublishedDate, replaceMode);
        handleFieldUpdate(e.getDescriptionLocked(), clear.isDescription(), m.getDescription(), v -> e.setDescription(nullIfBlank(v)), e::getDescription, replaceMode);
        handleFieldUpdate(e.getSeriesNameLocked(), clear.isSeriesName(), m.getSeriesName(), e::setSeriesName, e::getSeriesName, replaceMode);
        handleFieldUpdate(e.getSeriesNumberLocked(), clear.isSeriesNumber(), m.getSeriesNumber(), e::setSeriesNumber, e::getSeriesNumber, replaceMode);
        handleFieldUpdate(e.getSeriesTotalLocked(), clear.isSeriesTotal(), m.getSeriesTotal(), e::setSeriesTotal, e::getSeriesTotal, replaceMode);
        handleFieldUpdate(e.getIsbn13Locked(), clear.isIsbn13(), m.getIsbn13(), v -> e.setIsbn13(nullIfBlank(v)), e::getIsbn13, replaceMode);
        handleFieldUpdate(e.getIsbn10Locked(), clear.isIsbn10(), m.getIsbn10(), v -> e.setIsbn10(nullIfBlank(v)), e::getIsbn10, replaceMode);
        handleFieldUpdate(e.getAsinLocked(), clear.isAsin(), m.getAsin(), v -> e.setAsin(nullIfBlank(v)), e::getAsin, replaceMode);
        handleFieldUpdate(e.getGoodreadsIdLocked(), clear.isGoodreadsId(), m.getGoodreadsId(), v -> e.setGoodreadsId(nullIfBlank(v)), e::getGoodreadsId, replaceMode);
        handleFieldUpdate(e.getComicvineIdLocked(), clear.isComicvineId(), m.getComicvineId(), v -> e.setComicvineId(nullIfBlank(v)), e::getComicvineId, replaceMode);
        handleFieldUpdate(e.getHardcoverIdLocked(), clear.isHardcoverId(), m.getHardcoverId(), v -> e.setHardcoverId(nullIfBlank(v)), e::getHardcoverId, replaceMode);
        handleFieldUpdate(e.getHardcoverBookIdLocked(), clear.isHardcoverBookId(), m.getHardcoverBookId(), e::setHardcoverBookId, e::getHardcoverBookId, replaceMode);
        handleFieldUpdate(e.getGoogleIdLocked(), clear.isGoogleId(), m.getGoogleId(), v -> e.setGoogleId(nullIfBlank(v)), e::getGoogleId, replaceMode);
        handleFieldUpdate(e.getPageCountLocked(), clear.isPageCount(), m.getPageCount(), e::setPageCount, e::getPageCount, replaceMode);
        handleFieldUpdate(e.getLanguageLocked(), clear.isLanguage(), m.getLanguage(), v -> e.setLanguage(nullIfBlank(v)), e::getLanguage, replaceMode);
        handleFieldUpdate(e.getAmazonRatingLocked(), clear.isAmazonRating(), m.getAmazonRating(), e::setAmazonRating, e::getAmazonRating, replaceMode);
        handleFieldUpdate(e.getAmazonReviewCountLocked(), clear.isAmazonReviewCount(), m.getAmazonReviewCount(), e::setAmazonReviewCount, e::getAmazonReviewCount, replaceMode);
        handleFieldUpdate(e.getGoodreadsRatingLocked(), clear.isGoodreadsRating(), m.getGoodreadsRating(), e::setGoodreadsRating, e::getGoodreadsRating, replaceMode);
        handleFieldUpdate(e.getGoodreadsReviewCountLocked(), clear.isGoodreadsReviewCount(), m.getGoodreadsReviewCount(), e::setGoodreadsReviewCount, e::getGoodreadsReviewCount, replaceMode);
        handleFieldUpdate(e.getHardcoverRatingLocked(), clear.isHardcoverRating(), m.getHardcoverRating(), e::setHardcoverRating, e::getHardcoverRating, replaceMode);
        handleFieldUpdate(e.getHardcoverReviewCountLocked(), clear.isHardcoverReviewCount(), m.getHardcoverReviewCount(), e::setHardcoverReviewCount, e::getHardcoverReviewCount, replaceMode);
        handleFieldUpdate(e.getLubimyczytacIdLocked(), clear.isLubimyczytacId(), m.getLubimyczytacId(), v -> e.setLubimyczytacId(nullIfBlank(v)), () -> e.getLubimyczytacId(), replaceMode);
        handleFieldUpdate(e.getLubimyczytacRatingLocked(), clear.isLubimyczytacRating(), m.getLubimyczytacRating(), v -> e.setLubimyczytacRating(v), () -> e.getLubimyczytacRating(), replaceMode);
        handleFieldUpdate(e.getRanobedbIdLocked(), clear.isRanobedbId(), m.getRanobedbId(), v -> e.setRanobedbId(nullIfBlank(v)), e::getRanobedbId, replaceMode);
        handleFieldUpdate(e.getRanobedbRatingLocked(), clear.isRanobedbRating(), m.getRanobedbRating(), e::setRanobedbRating, e::getRanobedbRating, replaceMode);
        handleFieldUpdate(e.getAgeRatingLocked(), clear.isAgeRating(), m.getAgeRating(), e::setAgeRating, e::getAgeRating, replaceMode);
        handleFieldUpdate(e.getContentRatingLocked(), clear.isContentRating(), m.getContentRating(), v -> e.setContentRating(nullIfBlank(v)), e::getContentRating, replaceMode);
    }

    private <T> void handleFieldUpdate(Boolean locked, boolean shouldClear, T newValue, Consumer<T> setter, Supplier<T> getter, MetadataReplaceMode mode) {
        if (Boolean.TRUE.equals(locked)) return;
        if (shouldClear) {
            setter.accept(null);
            return;
        }
        if (mode == null) {
            if (newValue != null) setter.accept(newValue);
            return;
        }
        switch (mode) {
            case REPLACE_ALL -> setter.accept(newValue);
            case REPLACE_MISSING -> {
                if (isValueMissing(getter.get())) setter.accept(newValue);
            }
            case REPLACE_WHEN_PROVIDED -> {
                if (!isValueMissing(newValue)) setter.accept(newValue);
            }
        }
    }

    private <T> boolean isValueMissing(T value) {
        if (value == null) return true;
        if (value instanceof String) return !StringUtils.hasText((String) value);
        return false;
    }

    private void updateAuthorsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getAuthorsLocked())) return;

        e.setAuthors(Optional.ofNullable(e.getAuthors()).orElseGet(HashSet::new));

        if (clear.isAuthors()) {
            e.getAuthors().clear();
            return;
        }

        Set<String> authorNames = Optional.ofNullable(m.getAuthors()).orElse(Collections.emptySet());
        if (authorNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getAuthors().clear();
            return;
        }

        Set<AuthorEntity> newAuthors = authorNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> authorRepository.findByName(name)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newAuthors.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getAuthors().clear();
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getAuthors().isEmpty()) {
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        } else if (replaceMode == null) {
            if (!merge) e.getAuthors().clear();
            e.getAuthors().addAll(newAuthors);
            e.updateSearchText();
        }
    }

    private void updateCategoriesIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getCategoriesLocked())) return;

        e.setCategories(Optional.ofNullable(e.getCategories()).orElseGet(HashSet::new));

        if (clear.isCategories()) {
            e.getCategories().clear();
            return;
        }

        Set<String> categoryNames = Optional.ofNullable(m.getCategories()).orElse(Collections.emptySet());
        if (categoryNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getCategories().clear();
            return;
        }

        Set<CategoryEntity> newCategories = categoryNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> categoryRepository.findByName(name)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newCategories.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getCategories().clear();
            e.getCategories().addAll(newCategories);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getCategories().isEmpty()) {
            e.getCategories().addAll(newCategories);
        } else if (replaceMode == null) {
            if (!merge) e.getCategories().clear();
            e.getCategories().addAll(newCategories);
        }
    }

    private void updateMoodsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getMoodsLocked())) return;

        e.setMoods(Optional.ofNullable(e.getMoods()).orElseGet(HashSet::new));

        if (clear.isMoods()) {
            e.getMoods().clear();
            return;
        }

        Set<String> moodNames = Optional.ofNullable(m.getMoods()).orElse(Collections.emptySet());
        if (moodNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getMoods().clear();
            return;
        }

        Set<MoodEntity> newMoods = moodNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> moodRepository.findByName(name)
                        .orElseGet(() -> moodRepository.save(MoodEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newMoods.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getMoods().clear();
            e.getMoods().addAll(newMoods);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getMoods().isEmpty()) {
            e.getMoods().addAll(newMoods);
        } else if (replaceMode == null) {
            if (!merge) e.getMoods().clear();
            e.getMoods().addAll(newMoods);
        }
    }

    private void updateTagsIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, boolean merge, MetadataReplaceMode replaceMode) {
        if (Boolean.TRUE.equals(e.getTagsLocked())) return;

        e.setTags(Optional.ofNullable(e.getTags()).orElseGet(HashSet::new));

        if (clear.isTags()) {
            e.getTags().clear();
            return;
        }

        Set<String> tagNames = Optional.ofNullable(m.getTags()).orElse(Collections.emptySet());
        if (tagNames.isEmpty()) {
            if (replaceMode == MetadataReplaceMode.REPLACE_ALL) e.getTags().clear();
            return;
        }

        Set<TagEntity> newTags = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(TagEntity.builder().name(name).build())))
                .collect(Collectors.toSet());

        if (newTags.isEmpty()) return;

        if (replaceMode == MetadataReplaceMode.REPLACE_ALL || replaceMode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            if (!merge) e.getTags().clear();
            e.getTags().addAll(newTags);
        } else if (replaceMode == MetadataReplaceMode.REPLACE_MISSING && e.getTags().isEmpty()) {
            e.getTags().addAll(newTags);
        } else if (replaceMode == null) {
            if (!merge) e.getTags().clear();
            e.getTags().addAll(newTags);
        }
    }

    private void updateAudiobookMetadataIfNeeded(BookEntity bookEntity, BookMetadata m, BookMetadataEntity e, MetadataClearFlags clear, MetadataReplaceMode replaceMode) {
        if (clear == null) {
            clear = new MetadataClearFlags();
        }
        handleFieldUpdate(e.getNarratorLocked(), clear.isNarrator(), m.getNarrator(), v -> e.setNarrator(nullIfBlank(v)), e::getNarrator, replaceMode);
        handleFieldUpdate(e.getAbridgedLocked(), clear.isAbridged(), m.getAbridged(), e::setAbridged, e::getAbridged, replaceMode);
    }

    private void updateComicMetadataIfNeeded(BookMetadata m, BookMetadataEntity e, MetadataReplaceMode replaceMode) {
        ComicMetadata comicDto = m.getComicMetadata();
        if (comicDto == null) {
            return;
        }

        ComicMetadataEntity comic = e.getComicMetadata();
        if (comic == null) {
            comic = ComicMetadataEntity.builder()
                    .bookId(e.getBookId())
                    .bookMetadata(e)
                    .build();
            e.setComicMetadata(comic);
        }

        ComicMetadataEntity c = comic;

        // Update basic fields
        handleFieldUpdate(c.getIssueNumberLocked(), false, comicDto.getIssueNumber(), v -> c.setIssueNumber(nullIfBlank(v)), c::getIssueNumber, replaceMode);
        handleFieldUpdate(c.getVolumeNameLocked(), false, comicDto.getVolumeName(), v -> c.setVolumeName(nullIfBlank(v)), c::getVolumeName, replaceMode);
        handleFieldUpdate(c.getVolumeNumberLocked(), false, comicDto.getVolumeNumber(), c::setVolumeNumber, c::getVolumeNumber, replaceMode);
        handleFieldUpdate(c.getStoryArcLocked(), false, comicDto.getStoryArc(), v -> c.setStoryArc(nullIfBlank(v)), c::getStoryArc, replaceMode);
        handleFieldUpdate(null, false, comicDto.getStoryArcNumber(), c::setStoryArcNumber, c::getStoryArcNumber, replaceMode);
        handleFieldUpdate(null, false, comicDto.getAlternateSeries(), v -> c.setAlternateSeries(nullIfBlank(v)), c::getAlternateSeries, replaceMode);
        handleFieldUpdate(null, false, comicDto.getAlternateIssue(), v -> c.setAlternateIssue(nullIfBlank(v)), c::getAlternateIssue, replaceMode);
        handleFieldUpdate(null, false, comicDto.getImprint(), v -> c.setImprint(nullIfBlank(v)), c::getImprint, replaceMode);
        handleFieldUpdate(null, false, comicDto.getFormat(), v -> c.setFormat(nullIfBlank(v)), c::getFormat, replaceMode);
        handleFieldUpdate(null, false, comicDto.getBlackAndWhite(), c::setBlackAndWhite, c::getBlackAndWhite, replaceMode);
        handleFieldUpdate(null, false, comicDto.getManga(), c::setManga, c::getManga, replaceMode);
        handleFieldUpdate(null, false, comicDto.getReadingDirection(), v -> c.setReadingDirection(nullIfBlank(v)), c::getReadingDirection, replaceMode);
        handleFieldUpdate(null, false, comicDto.getWebLink(), v -> c.setWebLink(nullIfBlank(v)), c::getWebLink, replaceMode);
        handleFieldUpdate(null, false, comicDto.getNotes(), v -> c.setNotes(nullIfBlank(v)), c::getNotes, replaceMode);

        // Update relationships if not locked
        if (!Boolean.TRUE.equals(c.getCharactersLocked())) {
            updateComicCharacters(c, comicDto.getCharacters(), replaceMode);
        }
        if (!Boolean.TRUE.equals(c.getTeamsLocked())) {
            updateComicTeams(c, comicDto.getTeams(), replaceMode);
        }
        if (!Boolean.TRUE.equals(c.getLocationsLocked())) {
            updateComicLocations(c, comicDto.getLocations(), replaceMode);
        }
        if (!Boolean.TRUE.equals(c.getCreatorsLocked())) {
            updateComicCreators(c, comicDto, replaceMode);
        }

        // Update locks if provided
        if (comicDto.getIssueNumberLocked() != null) c.setIssueNumberLocked(comicDto.getIssueNumberLocked());
        if (comicDto.getVolumeNameLocked() != null) c.setVolumeNameLocked(comicDto.getVolumeNameLocked());
        if (comicDto.getVolumeNumberLocked() != null) c.setVolumeNumberLocked(comicDto.getVolumeNumberLocked());
        if (comicDto.getStoryArcLocked() != null) c.setStoryArcLocked(comicDto.getStoryArcLocked());
        if (comicDto.getCreatorsLocked() != null) c.setCreatorsLocked(comicDto.getCreatorsLocked());
        if (comicDto.getCharactersLocked() != null) c.setCharactersLocked(comicDto.getCharactersLocked());
        if (comicDto.getTeamsLocked() != null) c.setTeamsLocked(comicDto.getTeamsLocked());
        if (comicDto.getLocationsLocked() != null) c.setLocationsLocked(comicDto.getLocationsLocked());

        comicMetadataRepository.save(c);
    }

    private void updateComicCharacters(ComicMetadataEntity c, Set<String> characters, MetadataReplaceMode mode) {
        if (characters == null || characters.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                c.getCharacters().clear();
            }
            return;
        }
        if (c.getCharacters() == null) {
            c.setCharacters(new HashSet<>());
        }
        if (mode == MetadataReplaceMode.REPLACE_ALL || mode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            c.getCharacters().clear();
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !c.getCharacters().isEmpty()) {
            return;
        }
        characters.stream()
                .map(name -> comicCharacterRepository.findByName(name)
                        .orElseGet(() -> comicCharacterRepository.save(ComicCharacterEntity.builder().name(name).build())))
                .forEach(entity -> c.getCharacters().add(entity));
    }

    private void updateComicTeams(ComicMetadataEntity c, Set<String> teams, MetadataReplaceMode mode) {
        if (teams == null || teams.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                c.getTeams().clear();
            }
            return;
        }
        if (c.getTeams() == null) {
            c.setTeams(new HashSet<>());
        }
        if (mode == MetadataReplaceMode.REPLACE_ALL || mode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            c.getTeams().clear();
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !c.getTeams().isEmpty()) {
            return;
        }
        teams.stream()
                .map(name -> comicTeamRepository.findByName(name)
                        .orElseGet(() -> comicTeamRepository.save(ComicTeamEntity.builder().name(name).build())))
                .forEach(entity -> c.getTeams().add(entity));
    }

    private void updateComicLocations(ComicMetadataEntity c, Set<String> locations, MetadataReplaceMode mode) {
        if (locations == null || locations.isEmpty()) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                c.getLocations().clear();
            }
            return;
        }
        if (c.getLocations() == null) {
            c.setLocations(new HashSet<>());
        }
        if (mode == MetadataReplaceMode.REPLACE_ALL || mode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            c.getLocations().clear();
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !c.getLocations().isEmpty()) {
            return;
        }
        locations.stream()
                .map(name -> comicLocationRepository.findByName(name)
                        .orElseGet(() -> comicLocationRepository.save(ComicLocationEntity.builder().name(name).build())))
                .forEach(entity -> c.getLocations().add(entity));
    }

    private void updateComicCreators(ComicMetadataEntity c, ComicMetadata dto, MetadataReplaceMode mode) {
        if (c.getCreatorMappings() == null) {
            c.setCreatorMappings(new HashSet<>());
        }

        boolean hasNewCreators = (dto.getPencillers() != null && !dto.getPencillers().isEmpty()) ||
                (dto.getInkers() != null && !dto.getInkers().isEmpty()) ||
                (dto.getColorists() != null && !dto.getColorists().isEmpty()) ||
                (dto.getLetterers() != null && !dto.getLetterers().isEmpty()) ||
                (dto.getCoverArtists() != null && !dto.getCoverArtists().isEmpty()) ||
                (dto.getEditors() != null && !dto.getEditors().isEmpty());

        if (!hasNewCreators) {
            if (mode == MetadataReplaceMode.REPLACE_ALL) {
                c.getCreatorMappings().clear();
            }
            return;
        }

        if (mode == MetadataReplaceMode.REPLACE_ALL || mode == MetadataReplaceMode.REPLACE_WHEN_PROVIDED) {
            c.getCreatorMappings().clear();
        }
        if (mode == MetadataReplaceMode.REPLACE_MISSING && !c.getCreatorMappings().isEmpty()) {
            return;
        }

        addCreatorsWithRole(c, dto.getPencillers(), ComicCreatorRole.PENCILLER);
        addCreatorsWithRole(c, dto.getInkers(), ComicCreatorRole.INKER);
        addCreatorsWithRole(c, dto.getColorists(), ComicCreatorRole.COLORIST);
        addCreatorsWithRole(c, dto.getLetterers(), ComicCreatorRole.LETTERER);
        addCreatorsWithRole(c, dto.getCoverArtists(), ComicCreatorRole.COVER_ARTIST);
        addCreatorsWithRole(c, dto.getEditors(), ComicCreatorRole.EDITOR);
    }

    private void addCreatorsWithRole(ComicMetadataEntity comic, Set<String> names, ComicCreatorRole role) {
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String name : names) {
            ComicCreatorEntity creator = comicCreatorRepository.findByName(name)
                    .orElseGet(() -> comicCreatorRepository.save(ComicCreatorEntity.builder().name(name).build()));

            ComicCreatorMappingEntity mapping = ComicCreatorMappingEntity.builder()
                    .comicMetadata(comic)
                    .creator(creator)
                    .role(role)
                    .build();
            comic.getCreatorMappings().add(mapping);
        }
    }

    private void updateThumbnailIfNeeded(long bookId, BookEntity bookEntity, BookMetadata m, BookMetadataEntity e, boolean set) {
        if (Boolean.TRUE.equals(e.getCoverLocked())) {
            return;
        }
        if (!set) return;
        if (!StringUtils.hasText(m.getThumbnailUrl()) || isLocalOrPrivateUrl(m.getThumbnailUrl())) return;
        try {
            fileService.createThumbnailFromUrl(bookId, m.getThumbnailUrl());
            bookEntity.setBookCoverHash(BookCoverUtils.generateCoverHash());
            bookEntity.getMetadata().setCoverUpdatedOn(Instant.now());
        } catch (Exception ex) {
            log.warn("Failed to download cover for book {}: {}", bookId, ex.getMessage());
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
                Pair.of(m.getComicvineIdLocked(), e::setComicvineIdLocked),
                Pair.of(m.getHardcoverIdLocked(), e::setHardcoverIdLocked),
                Pair.of(m.getHardcoverBookIdLocked(), e::setHardcoverBookIdLocked),
                Pair.of(m.getLubimyczytacIdLocked(), e::setLubimyczytacIdLocked),
                Pair.of(m.getLubimyczytacRatingLocked(), e::setLubimyczytacRatingLocked),
                Pair.of(m.getGoogleIdLocked(), e::setGoogleIdLocked),
                Pair.of(m.getPageCountLocked(), e::setPageCountLocked),
                Pair.of(m.getLanguageLocked(), e::setLanguageLocked),
                Pair.of(m.getAmazonRatingLocked(), e::setAmazonRatingLocked),
                Pair.of(m.getAmazonReviewCountLocked(), e::setAmazonReviewCountLocked),
                Pair.of(m.getGoodreadsRatingLocked(), e::setGoodreadsRatingLocked),
                Pair.of(m.getGoodreadsReviewCountLocked(), e::setGoodreadsReviewCountLocked),
                Pair.of(m.getHardcoverRatingLocked(), e::setHardcoverRatingLocked),
                Pair.of(m.getHardcoverReviewCountLocked(), e::setHardcoverReviewCountLocked),
                Pair.of(m.getRanobedbIdLocked(), e::setRanobedbIdLocked),
                Pair.of(m.getRanobedbRatingLocked(), e::setRanobedbRatingLocked),
                Pair.of(m.getAudibleIdLocked(), e::setAudibleIdLocked),
                Pair.of(m.getAudibleRatingLocked(), e::setAudibleRatingLocked),
                Pair.of(m.getAudibleReviewCountLocked(), e::setAudibleReviewCountLocked),
                Pair.of(m.getCoverLocked(), e::setCoverLocked),
                Pair.of(m.getAudiobookCoverLocked(), e::setAudiobookCoverLocked),
                Pair.of(m.getAuthorsLocked(), e::setAuthorsLocked),
                Pair.of(m.getCategoriesLocked(), e::setCategoriesLocked),
                Pair.of(m.getMoodsLocked(), e::setMoodsLocked),
                Pair.of(m.getTagsLocked(), e::setTagsLocked),
                Pair.of(m.getReviewsLocked(), e::setReviewsLocked),
                Pair.of(m.getNarratorLocked(), e::setNarratorLocked),
                Pair.of(m.getAbridgedLocked(), e::setAbridgedLocked),
                Pair.of(m.getAgeRatingLocked(), e::setAgeRatingLocked),
                Pair.of(m.getContentRatingLocked(), e::setContentRatingLocked)
        );
        lockMappings.forEach(pair -> {
            if (pair.getLeft() != null) pair.getRight().accept(pair.getLeft());
        });
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean isLocalOrPrivateUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) return true;
            InetAddress addr = InetAddress.getByName(host);
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress();
        } catch (Exception e) {
            log.warn("Invalid thumbnail URL '{}': {}", url, e.getMessage());
            return true;
        }
    }
}
