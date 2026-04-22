package org.booklore.util;

import lombok.experimental.UtilityClass;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.ComicMetadata;
import org.booklore.model.entity.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

@UtilityClass
public class MetadataChangeDetector {

    private record FieldDescriptor<T>(
            String name,
            Function<BookMetadata, T> dtoValueGetter,
            Function<BookMetadataEntity, T> entityValueGetter,
            Function<BookMetadata, Boolean> dtoLockGetter,
            Function<BookMetadataEntity, Boolean> entityLockGetter,
            Predicate<MetadataClearFlags> clearFlagGetter,
            boolean includedInFileWrite
    ) {
        boolean isUnlocked(BookMetadataEntity entity) {
            return !isTrue(entityLockGetter.apply(entity));
        }

        boolean shouldClear(MetadataClearFlags flags) {
            return clearFlagGetter.test(flags);
        }

        T getNewValue(BookMetadata dto) {
            return dtoValueGetter.apply(dto);
        }

        T getOldValue(BookMetadataEntity entity) {
            return entityValueGetter.apply(entity);
        }

        Boolean getNewLock(BookMetadata dto) {
            return dtoLockGetter != null ? dtoLockGetter.apply(dto) : null;
        }

        Boolean getOldLock(BookMetadataEntity entity) {
            return entityLockGetter != null ? entityLockGetter.apply(entity) : null;
        }
    }

    private record CollectionFieldDescriptor(
            String name,
            Function<BookMetadata, ? extends Collection<String>> dtoValueGetter,
            Function<BookMetadataEntity, ? extends Collection<?>> entityValueGetter,
            Function<BookMetadata, Boolean> dtoLockGetter,
            Function<BookMetadataEntity, Boolean> entityLockGetter,
            Predicate<MetadataClearFlags> clearFlagGetter,
            boolean includedInFileWrite,
            boolean orderSensitive
    ) {
        CollectionFieldDescriptor(
                String name,
                Function<BookMetadata, ? extends Collection<String>> dtoValueGetter,
                Function<BookMetadataEntity, ? extends Collection<?>> entityValueGetter,
                Function<BookMetadata, Boolean> dtoLockGetter,
                Function<BookMetadataEntity, Boolean> entityLockGetter,
                Predicate<MetadataClearFlags> clearFlagGetter,
                boolean includedInFileWrite
        ) {
            this(name, dtoValueGetter, entityValueGetter, dtoLockGetter, entityLockGetter, clearFlagGetter, includedInFileWrite, false);
        }

        boolean isUnlocked(BookMetadataEntity entity) {
            return !isTrue(entityLockGetter.apply(entity));
        }

        boolean shouldClear(MetadataClearFlags flags) {
            return clearFlagGetter.test(flags);
        }

        Object getNewValue(BookMetadata dto) {
            Collection<String> values = dtoValueGetter.apply(dto);
            if (values == null) return null;
            return orderSensitive ? new ArrayList<>(values) : new HashSet<>(values);
        }

        Object getOldValue(BookMetadataEntity entity) {
            if (orderSensitive) {
                return toNameList(entityValueGetter.apply(entity));
            }
            return toNameSet(entityValueGetter.apply(entity));
        }

        Boolean getNewLock(BookMetadata dto) {
            return dtoLockGetter != null ? dtoLockGetter.apply(dto) : null;
        }

        Boolean getOldLock(BookMetadataEntity entity) {
            return entityLockGetter != null ? entityLockGetter.apply(entity) : null;
        }
    }

    private static final List<FieldDescriptor<?>> SIMPLE_FIELDS = List.of(
            new FieldDescriptor<>("title",
                    BookMetadata::getTitle, BookMetadataEntity::getTitle,
                    BookMetadata::getTitleLocked, BookMetadataEntity::getTitleLocked,
                    MetadataClearFlags::isTitle, true),
            new FieldDescriptor<>("subtitle",
                    BookMetadata::getSubtitle, BookMetadataEntity::getSubtitle,
                    BookMetadata::getSubtitleLocked, BookMetadataEntity::getSubtitleLocked,
                    MetadataClearFlags::isSubtitle, true),
            new FieldDescriptor<>("publisher",
                    BookMetadata::getPublisher, BookMetadataEntity::getPublisher,
                    BookMetadata::getPublisherLocked, BookMetadataEntity::getPublisherLocked,
                    MetadataClearFlags::isPublisher, true),
            new FieldDescriptor<>("publishedDate",
                    BookMetadata::getPublishedDate, BookMetadataEntity::getPublishedDate,
                    BookMetadata::getPublishedDateLocked, BookMetadataEntity::getPublishedDateLocked,
                    MetadataClearFlags::isPublishedDate, true),
            new FieldDescriptor<>("description",
                    BookMetadata::getDescription, BookMetadataEntity::getDescription,
                    BookMetadata::getDescriptionLocked, BookMetadataEntity::getDescriptionLocked,
                    MetadataClearFlags::isDescription, true),
            new FieldDescriptor<>("seriesName",
                    BookMetadata::getSeriesName, BookMetadataEntity::getSeriesName,
                    BookMetadata::getSeriesNameLocked, BookMetadataEntity::getSeriesNameLocked,
                    MetadataClearFlags::isSeriesName, true),
            new FieldDescriptor<>("seriesNumber",
                    BookMetadata::getSeriesNumber, BookMetadataEntity::getSeriesNumber,
                    BookMetadata::getSeriesNumberLocked, BookMetadataEntity::getSeriesNumberLocked,
                    MetadataClearFlags::isSeriesNumber, true),
            new FieldDescriptor<>("seriesTotal",
                    BookMetadata::getSeriesTotal, BookMetadataEntity::getSeriesTotal,
                    BookMetadata::getSeriesTotalLocked, BookMetadataEntity::getSeriesTotalLocked,
                    MetadataClearFlags::isSeriesTotal, true),
            new FieldDescriptor<>("isbn13",
                    BookMetadata::getIsbn13, BookMetadataEntity::getIsbn13,
                    BookMetadata::getIsbn13Locked, BookMetadataEntity::getIsbn13Locked,
                    MetadataClearFlags::isIsbn13, true),
            new FieldDescriptor<>("isbn10",
                    BookMetadata::getIsbn10, BookMetadataEntity::getIsbn10,
                    BookMetadata::getIsbn10Locked, BookMetadataEntity::getIsbn10Locked,
                    MetadataClearFlags::isIsbn10, true),
            new FieldDescriptor<>("asin",
                    BookMetadata::getAsin, BookMetadataEntity::getAsin,
                    BookMetadata::getAsinLocked, BookMetadataEntity::getAsinLocked,
                    MetadataClearFlags::isAsin, true),
            new FieldDescriptor<>("goodreadsId",
                    BookMetadata::getGoodreadsId, BookMetadataEntity::getGoodreadsId,
                    BookMetadata::getGoodreadsIdLocked, BookMetadataEntity::getGoodreadsIdLocked,
                    MetadataClearFlags::isGoodreadsId, true),
            new FieldDescriptor<>("comicvineId",
                    BookMetadata::getComicvineId, BookMetadataEntity::getComicvineId,
                    BookMetadata::getComicvineIdLocked, BookMetadataEntity::getComicvineIdLocked,
                    MetadataClearFlags::isComicvineId, true),
            new FieldDescriptor<>("hardcoverId",
                    BookMetadata::getHardcoverId, BookMetadataEntity::getHardcoverId,
                    BookMetadata::getHardcoverIdLocked, BookMetadataEntity::getHardcoverIdLocked,
                    MetadataClearFlags::isHardcoverId, true),
            new FieldDescriptor<>("hardcoverBookId",
                    BookMetadata::getHardcoverBookId, BookMetadataEntity::getHardcoverBookId,
                    BookMetadata::getHardcoverBookIdLocked, BookMetadataEntity::getHardcoverBookIdLocked,
                    MetadataClearFlags::isHardcoverBookId, true),
            new FieldDescriptor<>("googleId",
                    BookMetadata::getGoogleId, BookMetadataEntity::getGoogleId,
                    BookMetadata::getGoogleIdLocked, BookMetadataEntity::getGoogleIdLocked,
                    MetadataClearFlags::isGoogleId, true),
            new FieldDescriptor<>("lubimyczytacId",
                    BookMetadata::getLubimyczytacId, BookMetadataEntity::getLubimyczytacId,
                    BookMetadata::getLubimyczytacIdLocked, BookMetadataEntity::getLubimyczytacIdLocked,
                    MetadataClearFlags::isLubimyczytacId, true),
            new FieldDescriptor<>("ranobedbId",
                    BookMetadata::getRanobedbId, BookMetadataEntity::getRanobedbId,
                    BookMetadata::getRanobedbIdLocked, BookMetadataEntity::getRanobedbIdLocked,
                    MetadataClearFlags::isRanobedbId, true),
            new FieldDescriptor<>("language",
                    BookMetadata::getLanguage, BookMetadataEntity::getLanguage,
                    BookMetadata::getLanguageLocked, BookMetadataEntity::getLanguageLocked,
                    MetadataClearFlags::isLanguage, true),
            new FieldDescriptor<>("pageCount",
                    BookMetadata::getPageCount, BookMetadataEntity::getPageCount,
                    BookMetadata::getPageCountLocked, BookMetadataEntity::getPageCountLocked,
                    MetadataClearFlags::isPageCount, true),
            new FieldDescriptor<>("amazonRating",
                    BookMetadata::getAmazonRating, BookMetadataEntity::getAmazonRating,
                    BookMetadata::getAmazonRatingLocked, BookMetadataEntity::getAmazonRatingLocked,
                    MetadataClearFlags::isAmazonRating, true),
            new FieldDescriptor<>("amazonReviewCount",
                    BookMetadata::getAmazonReviewCount, BookMetadataEntity::getAmazonReviewCount,
                    BookMetadata::getAmazonReviewCountLocked, BookMetadataEntity::getAmazonReviewCountLocked,
                    MetadataClearFlags::isAmazonReviewCount, true),
            new FieldDescriptor<>("goodreadsRating",
                    BookMetadata::getGoodreadsRating, BookMetadataEntity::getGoodreadsRating,
                    BookMetadata::getGoodreadsRatingLocked, BookMetadataEntity::getGoodreadsRatingLocked,
                    MetadataClearFlags::isGoodreadsRating, true),
            new FieldDescriptor<>("goodreadsReviewCount",
                    BookMetadata::getGoodreadsReviewCount, BookMetadataEntity::getGoodreadsReviewCount,
                    BookMetadata::getGoodreadsReviewCountLocked, BookMetadataEntity::getGoodreadsReviewCountLocked,
                    MetadataClearFlags::isGoodreadsReviewCount, true),
            new FieldDescriptor<>("hardcoverRating",
                    BookMetadata::getHardcoverRating, BookMetadataEntity::getHardcoverRating,
                    BookMetadata::getHardcoverRatingLocked, BookMetadataEntity::getHardcoverRatingLocked,
                    MetadataClearFlags::isHardcoverRating, true),
            new FieldDescriptor<>("hardcoverReviewCount",
                    BookMetadata::getHardcoverReviewCount, BookMetadataEntity::getHardcoverReviewCount,
                    BookMetadata::getHardcoverReviewCountLocked, BookMetadataEntity::getHardcoverReviewCountLocked,
                    MetadataClearFlags::isHardcoverReviewCount, true),
            new FieldDescriptor<>("lubimyczytacRating",
                    BookMetadata::getLubimyczytacRating, BookMetadataEntity::getLubimyczytacRating,
                    BookMetadata::getLubimyczytacRatingLocked, BookMetadataEntity::getLubimyczytacRatingLocked,
                    MetadataClearFlags::isLubimyczytacRating, true),
            new FieldDescriptor<>("ranobedbRating",
                    BookMetadata::getRanobedbRating, BookMetadataEntity::getRanobedbRating,
                    BookMetadata::getRanobedbRatingLocked, BookMetadataEntity::getRanobedbRatingLocked,
                    MetadataClearFlags::isRanobedbRating, true),
            new FieldDescriptor<>("audibleId",
                    BookMetadata::getAudibleId, BookMetadataEntity::getAudibleId,
                    BookMetadata::getAudibleIdLocked, BookMetadataEntity::getAudibleIdLocked,
                    MetadataClearFlags::isAudibleId, false),
            new FieldDescriptor<>("audibleRating",
                    BookMetadata::getAudibleRating, BookMetadataEntity::getAudibleRating,
                    BookMetadata::getAudibleRatingLocked, BookMetadataEntity::getAudibleRatingLocked,
                    MetadataClearFlags::isAudibleRating, false),
            new FieldDescriptor<>("audibleReviewCount",
                    BookMetadata::getAudibleReviewCount, BookMetadataEntity::getAudibleReviewCount,
                    BookMetadata::getAudibleReviewCountLocked, BookMetadataEntity::getAudibleReviewCountLocked,
                    MetadataClearFlags::isAudibleReviewCount, false),
            new FieldDescriptor<>("narrator",
                    BookMetadata::getNarrator, BookMetadataEntity::getNarrator,
                    BookMetadata::getNarratorLocked, BookMetadataEntity::getNarratorLocked,
                    MetadataClearFlags::isNarrator, false),
            new FieldDescriptor<>("abridged",
                    BookMetadata::getAbridged, BookMetadataEntity::getAbridged,
                    BookMetadata::getAbridgedLocked, BookMetadataEntity::getAbridgedLocked,
                    MetadataClearFlags::isAbridged, false),
            new FieldDescriptor<>("ageRating",
                    BookMetadata::getAgeRating, BookMetadataEntity::getAgeRating,
                    BookMetadata::getAgeRatingLocked, BookMetadataEntity::getAgeRatingLocked,
                    MetadataClearFlags::isAgeRating, true),
            new FieldDescriptor<>("contentRating",
                    BookMetadata::getContentRating, BookMetadataEntity::getContentRating,
                    BookMetadata::getContentRatingLocked, BookMetadataEntity::getContentRatingLocked,
                    MetadataClearFlags::isContentRating, true)
    );

    private static final List<CollectionFieldDescriptor> COLLECTION_FIELDS = List.of(
            new CollectionFieldDescriptor("authors",
                    BookMetadata::getAuthors, BookMetadataEntity::getAuthors,
                    BookMetadata::getAuthorsLocked, BookMetadataEntity::getAuthorsLocked,
                    MetadataClearFlags::isAuthors, true, true),
            new CollectionFieldDescriptor("categories",
                    BookMetadata::getCategories, BookMetadataEntity::getCategories,
                    BookMetadata::getCategoriesLocked, BookMetadataEntity::getCategoriesLocked,
                    MetadataClearFlags::isCategories, true),
            new CollectionFieldDescriptor("moods",
                    BookMetadata::getMoods, BookMetadataEntity::getMoods,
                    BookMetadata::getMoodsLocked, BookMetadataEntity::getMoodsLocked,
                    MetadataClearFlags::isMoods, true),
            new CollectionFieldDescriptor("tags",
                    BookMetadata::getTags, BookMetadataEntity::getTags,
                    BookMetadata::getTagsLocked, BookMetadataEntity::getTagsLocked,
                    MetadataClearFlags::isTags, true)
    );

    public static boolean isDifferent(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        if (clear == null) return true;
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (hasFieldDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (hasCollectionFieldDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        if (hasComicMetadataChanges(newMeta, existingMeta)) {
            return true;
        }
        if (hasComicLockChanges(newMeta, existingMeta)) {
            return true;
        }
        return differsLock(newMeta.getCoverLocked(), existingMeta.getCoverLocked()) || differsLock(newMeta.getAudiobookCoverLocked(), existingMeta.getAudiobookCoverLocked());
    }

    public static boolean hasValueChanges(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (hasValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (hasCollectionValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        if (hasComicMetadataChanges(newMeta, existingMeta)) {
            return true;
        }
        return false;
    }

    public static boolean hasValueChangesForFileWrite(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (field.includedInFileWrite() && hasValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (field.includedInFileWrite() && hasCollectionValueDifference(field, newMeta, existingMeta, clear)) {
                return true;
            }
        }
        return false;
    }

    private static <T> boolean hasFieldDifference(FieldDescriptor<T> field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        boolean valueChanged = differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
        boolean lockChanged = differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta));
        return valueChanged || lockChanged;
    }

    private static boolean hasCollectionFieldDifference(CollectionFieldDescriptor field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        boolean valueChanged = differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
        boolean lockChanged = differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta));
        return valueChanged || lockChanged;
    }

    private static <T> boolean hasValueDifference(FieldDescriptor<T> field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        return differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
    }

    private static boolean hasCollectionValueDifference(CollectionFieldDescriptor field, BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        return differs(
                field.shouldClear(clear),
                field.getNewValue(newMeta),
                field.getOldValue(existingMeta),
                field.isUnlocked(existingMeta)
        );
    }

    private static boolean differs(boolean shouldClear, Object newVal, Object oldVal, boolean isUnlocked) {
        if (!isUnlocked) return false;

        Object normNew = normalize(newVal);
        Object normOld = normalize(oldVal);

        // Ignore transitions from null to empty string or empty set
        if (normOld == null && isEffectivelyEmpty(normNew)) return false;
        if (shouldClear) return normOld != null;

        return !Objects.equals(normNew, normOld);
    }

    private static boolean isEffectivelyEmpty(Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.isBlank();
            case Collection<?> c -> c.isEmpty();
            default -> false;
        };
    }

    private static boolean differsLock(Boolean dtoLock, Boolean entityLock) {
        return !Objects.equals(Boolean.TRUE.equals(dtoLock), Boolean.TRUE.equals(entityLock));
    }

    private static Object normalize(Object value) {
        if (value instanceof String s) return s.strip();
        return value;
    }

    private static boolean differsValue(Object newVal, Object oldVal) {
        Object normNew = normalize(newVal);
        Object normOld = normalize(oldVal);
        if (normOld == null && isEffectivelyEmpty(normNew)) return false;
        if (normNew == null && isEffectivelyEmpty(normOld)) return false;
        return !Objects.equals(normNew, normOld);
    }

    private static List<String> toNameList(Collection<?> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(e -> switch (e) {
                    case AuthorEntity author -> author.getName();
                    case CategoryEntity category -> category.getName();
                    case MoodEntity mood -> mood.getName();
                    case TagEntity tag -> tag.getName();
                    default -> e.toString();
                })
                .toList();
    }

    private static Set<String> toNameSet(Collection<?> entities) {
        if (entities == null) {
            return Collections.emptySet();
        }
        return entities.stream()
                .map(e -> switch (e) {
                    case AuthorEntity author -> author.getName();
                    case CategoryEntity category -> category.getName();
                    case MoodEntity mood -> mood.getName();
                    case TagEntity tag -> tag.getName();
                    default -> e.toString();
                })
                .collect(Collectors.toSet());
    }

    private static boolean hasComicMetadataChanges(BookMetadata newMeta, BookMetadataEntity existingMeta) {
        ComicMetadata comicDto = newMeta.getComicMetadata();
        ComicMetadataEntity comicEntity = existingMeta.getComicMetadata();

        // No comic metadata in DTO, no changes
        if (comicDto == null) {
            return false;
        }

        // Comic metadata in DTO but not in entity - only a value change if DTO has actual data
        if (comicEntity == null) {
            return hasNonEmptyComicValue(comicDto);
        }

        // Compare individual fields (using differsValue to treat null and empty as equivalent)
        return differsValue(comicDto.getIssueNumber(), comicEntity.getIssueNumber())
                || differsValue(comicDto.getVolumeName(), comicEntity.getVolumeName())
                || differsValue(comicDto.getVolumeNumber(), comicEntity.getVolumeNumber())
                || differsValue(comicDto.getStoryArc(), comicEntity.getStoryArc())
                || differsValue(comicDto.getStoryArcNumber(), comicEntity.getStoryArcNumber())
                || differsValue(comicDto.getAlternateSeries(), comicEntity.getAlternateSeries())
                || differsValue(comicDto.getAlternateIssue(), comicEntity.getAlternateIssue())
                || differsValue(comicDto.getImprint(), comicEntity.getImprint())
                || differsValue(comicDto.getFormat(), comicEntity.getFormat())
                || differsValue(comicDto.getBlackAndWhite(), comicEntity.getBlackAndWhite())
                || differsValue(comicDto.getManga(), comicEntity.getManga())
                || differsValue(comicDto.getReadingDirection(), comicEntity.getReadingDirection())
                || differsValue(comicDto.getWebLink(), comicEntity.getWebLink())
                || differsValue(comicDto.getNotes(), comicEntity.getNotes())
                || !stringSetsEqual(comicDto.getCharacters(), extractCharacterNames(comicEntity.getCharacters()))
                || !stringSetsEqual(comicDto.getTeams(), extractTeamNames(comicEntity.getTeams()))
                || !stringSetsEqual(comicDto.getLocations(), extractLocationNames(comicEntity.getLocations()))
                || hasCreatorChanges(comicDto, comicEntity);
    }

    private static boolean hasComicLockChanges(BookMetadata newMeta, BookMetadataEntity existingMeta) {
        ComicMetadata comicDto = newMeta.getComicMetadata();
        ComicMetadataEntity comicEntity = existingMeta.getComicMetadata();
        if (comicDto == null) return false;
        if (comicEntity == null) return false;
        return differsLock(comicDto.getIssueNumberLocked(), comicEntity.getIssueNumberLocked())
                || differsLock(comicDto.getVolumeNameLocked(), comicEntity.getVolumeNameLocked())
                || differsLock(comicDto.getVolumeNumberLocked(), comicEntity.getVolumeNumberLocked())
                || differsLock(comicDto.getStoryArcLocked(), comicEntity.getStoryArcLocked())
                || differsLock(comicDto.getStoryArcNumberLocked(), comicEntity.getStoryArcNumberLocked())
                || differsLock(comicDto.getAlternateSeriesLocked(), comicEntity.getAlternateSeriesLocked())
                || differsLock(comicDto.getAlternateIssueLocked(), comicEntity.getAlternateIssueLocked())
                || differsLock(comicDto.getImprintLocked(), comicEntity.getImprintLocked())
                || differsLock(comicDto.getFormatLocked(), comicEntity.getFormatLocked())
                || differsLock(comicDto.getBlackAndWhiteLocked(), comicEntity.getBlackAndWhiteLocked())
                || differsLock(comicDto.getMangaLocked(), comicEntity.getMangaLocked())
                || differsLock(comicDto.getReadingDirectionLocked(), comicEntity.getReadingDirectionLocked())
                || differsLock(comicDto.getWebLinkLocked(), comicEntity.getWebLinkLocked())
                || differsLock(comicDto.getNotesLocked(), comicEntity.getNotesLocked())
                || differsLock(comicDto.getCreatorsLocked(), comicEntity.getCreatorsLocked())
                || differsLock(comicDto.getPencillersLocked(), comicEntity.getPencillersLocked())
                || differsLock(comicDto.getInkersLocked(), comicEntity.getInkersLocked())
                || differsLock(comicDto.getColoristsLocked(), comicEntity.getColoristsLocked())
                || differsLock(comicDto.getLetterersLocked(), comicEntity.getLetterersLocked())
                || differsLock(comicDto.getCoverArtistsLocked(), comicEntity.getCoverArtistsLocked())
                || differsLock(comicDto.getEditorsLocked(), comicEntity.getEditorsLocked())
                || differsLock(comicDto.getCharactersLocked(), comicEntity.getCharactersLocked())
                || differsLock(comicDto.getTeamsLocked(), comicEntity.getTeamsLocked())
                || differsLock(comicDto.getLocationsLocked(), comicEntity.getLocationsLocked());
    }

    public static boolean hasLockChanges(BookMetadata newMeta, BookMetadataEntity existingMeta) {
        for (FieldDescriptor<?> field : SIMPLE_FIELDS) {
            if (differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta))) {
                return true;
            }
        }
        for (CollectionFieldDescriptor field : COLLECTION_FIELDS) {
            if (differsLock(field.getNewLock(newMeta), field.getOldLock(existingMeta))) {
                return true;
            }
        }
        if (differsLock(newMeta.getCoverLocked(), existingMeta.getCoverLocked())) return true;
        if (differsLock(newMeta.getAudiobookCoverLocked(), existingMeta.getAudiobookCoverLocked())) return true;
        if (differsLock(newMeta.getReviewsLocked(), existingMeta.getReviewsLocked())) return true;
        return hasComicLockChanges(newMeta, existingMeta);
    }

    private static boolean hasNonEmptyComicValue(ComicMetadata dto) {
        return !isEffectivelyEmpty(dto.getIssueNumber())
                || !isEffectivelyEmpty(dto.getVolumeName())
                || dto.getVolumeNumber() != null
                || !isEffectivelyEmpty(dto.getStoryArc())
                || dto.getStoryArcNumber() != null
                || !isEffectivelyEmpty(dto.getAlternateSeries())
                || !isEffectivelyEmpty(dto.getAlternateIssue())
                || !isEffectivelyEmpty(dto.getImprint())
                || !isEffectivelyEmpty(dto.getFormat())
                || dto.getBlackAndWhite() != null
                || dto.getManga() != null
                || !isEffectivelyEmpty(dto.getReadingDirection())
                || !isEffectivelyEmpty(dto.getWebLink())
                || !isEffectivelyEmpty(dto.getNotes())
                || (dto.getCharacters() != null && !dto.getCharacters().isEmpty())
                || (dto.getTeams() != null && !dto.getTeams().isEmpty())
                || (dto.getLocations() != null && !dto.getLocations().isEmpty())
                || (dto.getPencillers() != null && !dto.getPencillers().isEmpty())
                || (dto.getInkers() != null && !dto.getInkers().isEmpty())
                || (dto.getColorists() != null && !dto.getColorists().isEmpty())
                || (dto.getLetterers() != null && !dto.getLetterers().isEmpty())
                || (dto.getCoverArtists() != null && !dto.getCoverArtists().isEmpty())
                || (dto.getEditors() != null && !dto.getEditors().isEmpty());
    }

    private static boolean stringSetsEqual(Set<String> set1, Set<String> set2) {
        if (set1 == null && (set2 == null || set2.isEmpty())) return true;
        if (set1 == null || set2 == null) return false;
        if (set1.isEmpty() && set2.isEmpty()) return true;
        return set1.equals(set2);
    }

    private static Set<String> extractCharacterNames(Set<ComicCharacterEntity> entities) {
        if (entities == null) return Collections.emptySet();
        return entities.stream().map(ComicCharacterEntity::getName).collect(Collectors.toSet());
    }

    private static Set<String> extractTeamNames(Set<ComicTeamEntity> entities) {
        if (entities == null) return Collections.emptySet();
        return entities.stream().map(ComicTeamEntity::getName).collect(Collectors.toSet());
    }

    private static Set<String> extractLocationNames(Set<ComicLocationEntity> entities) {
        if (entities == null) return Collections.emptySet();
        return entities.stream().map(ComicLocationEntity::getName).collect(Collectors.toSet());
    }

    private static boolean hasCreatorChanges(ComicMetadata dto, ComicMetadataEntity entity) {
        // For creators, we do a simplified comparison based on whether there are any creators in DTO
        boolean dtoHasCreators = (dto.getPencillers() != null && !dto.getPencillers().isEmpty())
                || (dto.getInkers() != null && !dto.getInkers().isEmpty())
                || (dto.getColorists() != null && !dto.getColorists().isEmpty())
                || (dto.getLetterers() != null && !dto.getLetterers().isEmpty())
                || (dto.getCoverArtists() != null && !dto.getCoverArtists().isEmpty())
                || (dto.getEditors() != null && !dto.getEditors().isEmpty());

        boolean entityHasCreators = entity.getCreatorMappings() != null && !entity.getCreatorMappings().isEmpty();

        // If both have no creators, no change
        if (!dtoHasCreators && !entityHasCreators) return false;

        // If one has creators and other doesn't, there's a change
        if (dtoHasCreators != entityHasCreators) return true;

        // Both have creators - compare counts as a basic check
        int dtoCount = countNonNull(dto.getPencillers()) + countNonNull(dto.getInkers())
                + countNonNull(dto.getColorists()) + countNonNull(dto.getLetterers())
                + countNonNull(dto.getCoverArtists()) + countNonNull(dto.getEditors());

        return dtoCount != entity.getCreatorMappings().size();
    }

    private static int countNonNull(Set<String> set) {
        return set == null ? 0 : set.size();
    }
}
