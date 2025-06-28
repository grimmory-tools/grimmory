package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MetadataChangeDetector {

    private static final Logger log = LoggerFactory.getLogger(MetadataChangeDetector.class);

    public static boolean isDifferent(BookMetadata newMeta, BookMetadataEntity existingMeta, MetadataClearFlags clear) {
        if (clear == null) return true;

        List<String> changes = new ArrayList<>();

        compare(changes, "title", clear.isTitle(), newMeta.getTitle(), existingMeta.getTitle(), () -> !isTrue(existingMeta.getTitleLocked()), newMeta.getTitleLocked(), existingMeta.getTitleLocked());
        compare(changes, "subtitle", clear.isSubtitle(), newMeta.getSubtitle(), existingMeta.getSubtitle(), () -> !isTrue(existingMeta.getSubtitleLocked()), newMeta.getSubtitleLocked(), existingMeta.getSubtitleLocked());
        compare(changes, "publisher", clear.isPublisher(), newMeta.getPublisher(), existingMeta.getPublisher(), () -> !isTrue(existingMeta.getPublisherLocked()), newMeta.getPublisherLocked(), existingMeta.getPublisherLocked());
        compare(changes, "publishedDate", clear.isPublishedDate(), newMeta.getPublishedDate(), existingMeta.getPublishedDate(), () -> !isTrue(existingMeta.getPublishedDateLocked()), newMeta.getPublishedDateLocked(), existingMeta.getPublishedDateLocked());
        compare(changes, "description", clear.isDescription(), newMeta.getDescription(), existingMeta.getDescription(), () -> !isTrue(existingMeta.getDescriptionLocked()), newMeta.getDescriptionLocked(), existingMeta.getDescriptionLocked());
        compare(changes, "seriesName", clear.isSeriesName(), newMeta.getSeriesName(), existingMeta.getSeriesName(), () -> !isTrue(existingMeta.getSeriesNameLocked()), newMeta.getSeriesNameLocked(), existingMeta.getSeriesNameLocked());
        compare(changes, "seriesNumber", clear.isSeriesNumber(), newMeta.getSeriesNumber(), existingMeta.getSeriesNumber(), () -> !isTrue(existingMeta.getSeriesNumberLocked()), newMeta.getSeriesNumberLocked(), existingMeta.getSeriesNumberLocked());
        compare(changes, "seriesTotal", clear.isSeriesTotal(), newMeta.getSeriesTotal(), existingMeta.getSeriesTotal(), () -> !isTrue(existingMeta.getSeriesTotalLocked()), newMeta.getSeriesTotalLocked(), existingMeta.getSeriesTotalLocked());
        compare(changes, "isbn13", clear.isIsbn13(), newMeta.getIsbn13(), existingMeta.getIsbn13(), () -> !isTrue(existingMeta.getIsbn13Locked()), newMeta.getIsbn13Locked(), existingMeta.getIsbn13Locked());
        compare(changes, "isbn10", clear.isIsbn10(), newMeta.getIsbn10(), existingMeta.getIsbn10(), () -> !isTrue(existingMeta.getIsbn10Locked()), newMeta.getIsbn10Locked(), existingMeta.getIsbn10Locked());
        compare(changes, "asin", clear.isAsin(), newMeta.getAsin(), existingMeta.getAsin(), () -> !isTrue(existingMeta.getAsinLocked()), newMeta.getAsinLocked(), existingMeta.getAsinLocked());
        compare(changes, "goodreadsId", clear.isGoodreadsId(), newMeta.getGoodreadsId(), existingMeta.getGoodreadsId(), () -> !isTrue(existingMeta.getGoodreadsIdLocked()), newMeta.getGoodreadsIdLocked(), existingMeta.getGoodreadsIdLocked());
        compare(changes, "hardcoverId", clear.isHardcoverId(), newMeta.getHardcoverId(), existingMeta.getHardcoverId(), () -> !isTrue(existingMeta.getHardcoverIdLocked()), newMeta.getHardcoverIdLocked(), existingMeta.getHardcoverIdLocked());
        compare(changes, "googleId", clear.isGoogleId(), newMeta.getGoogleId(), existingMeta.getGoogleId(), () -> !isTrue(existingMeta.getGoogleIdLocked()), newMeta.getGoogleIdLocked(), existingMeta.getGoogleIdLocked());
        compare(changes, "pageCount", clear.isPageCount(), newMeta.getPageCount(), existingMeta.getPageCount(), () -> !isTrue(existingMeta.getPageCountLocked()), newMeta.getPageCountLocked(), existingMeta.getPageCountLocked());
        compare(changes, "language", clear.isLanguage(), newMeta.getLanguage(), existingMeta.getLanguage(), () -> !isTrue(existingMeta.getLanguageLocked()), newMeta.getLanguageLocked(), existingMeta.getLanguageLocked());
        compare(changes, "personalRating", clear.isPersonalRating(), newMeta.getPersonalRating(), existingMeta.getPersonalRating(), () -> !isTrue(existingMeta.getPersonalRatingLocked()), newMeta.getPersonalRatingLocked(), existingMeta.getPersonalRatingLocked());
        compare(changes, "amazonRating", clear.isAmazonRating(), newMeta.getAmazonRating(), existingMeta.getAmazonRating(), () -> !isTrue(existingMeta.getAmazonRatingLocked()), newMeta.getAmazonRatingLocked(), existingMeta.getAmazonRatingLocked());
        compare(changes, "amazonReviewCount", clear.isAmazonReviewCount(), newMeta.getAmazonReviewCount(), existingMeta.getAmazonReviewCount(), () -> !isTrue(existingMeta.getAmazonReviewCountLocked()), newMeta.getAmazonReviewCountLocked(), existingMeta.getAmazonReviewCountLocked());
        compare(changes, "goodreadsRating", clear.isGoodreadsRating(), newMeta.getGoodreadsRating(), existingMeta.getGoodreadsRating(), () -> !isTrue(existingMeta.getGoodreadsRatingLocked()), newMeta.getGoodreadsRatingLocked(), existingMeta.getGoodreadsRatingLocked());
        compare(changes, "goodreadsReviewCount", clear.isGoodreadsReviewCount(), newMeta.getGoodreadsReviewCount(), existingMeta.getGoodreadsReviewCount(), () -> !isTrue(existingMeta.getGoodreadsReviewCountLocked()), newMeta.getGoodreadsReviewCountLocked(), existingMeta.getGoodreadsReviewCountLocked());
        compare(changes, "hardcoverRating", clear.isHardcoverRating(), newMeta.getHardcoverRating(), existingMeta.getHardcoverRating(), () -> !isTrue(existingMeta.getHardcoverRatingLocked()), newMeta.getHardcoverRatingLocked(), existingMeta.getHardcoverRatingLocked());
        compare(changes, "hardcoverReviewCount", clear.isHardcoverReviewCount(), newMeta.getHardcoverReviewCount(), existingMeta.getHardcoverReviewCount(), () -> !isTrue(existingMeta.getHardcoverReviewCountLocked()), newMeta.getHardcoverReviewCountLocked(), existingMeta.getHardcoverReviewCountLocked());
        compare(changes, "authors", clear.isAuthors(), newMeta.getAuthors(), toNameSet(existingMeta.getAuthors()), () -> !isTrue(existingMeta.getAuthorsLocked()), newMeta.getAuthorsLocked(), existingMeta.getAuthorsLocked());
        compare(changes, "categories", clear.isCategories(), newMeta.getCategories(), toNameSet(existingMeta.getCategories()), () -> !isTrue(existingMeta.getCategoriesLocked()), newMeta.getCategoriesLocked(), existingMeta.getCategoriesLocked());

        if (!changes.isEmpty()) {
            /*changes.forEach(change -> log.info("Metadata change: {}", change));*/
            return true;
        }

        return false;
    }

    private static void compare(List<String> diffs, String field, boolean shouldClear, Object newVal, Object oldVal, Supplier<Boolean> isUnlocked, Boolean newLock, Boolean oldLock) {
        boolean valueChanged = differs(shouldClear, newVal, oldVal, isUnlocked);
        boolean lockChanged = differsLock(newLock, oldLock);

        if (valueChanged || lockChanged) {
            StringBuilder sb = new StringBuilder();
            sb.append(field);
            if (valueChanged) sb.append(" value: [").append(safe(oldVal)).append("] → [").append(safe(newVal)).append("]");
            if (lockChanged) sb.append(" lock: [").append(isTrue(oldLock)).append("] → [").append(isTrue(newLock)).append("]");
            diffs.add(sb.toString());
        }
    }

    private static boolean differs(boolean shouldClear, Object newVal, Object oldVal, Supplier<Boolean> isUnlocked) {
        if (!isUnlocked.get()) return false;

        Object normNew = normalize(newVal);
        Object normOld = normalize(oldVal);

        // Ignore transitions from null to empty string or empty set
        if (normOld == null && isEffectivelyEmpty(normNew)) return false;
        if (shouldClear) return normOld != null;

        return !Objects.equals(normNew, normOld);
    }

    private static boolean isEffectivelyEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private static boolean differsLock(Boolean dtoLock, Boolean entityLock) {
        return !Objects.equals(Boolean.TRUE.equals(dtoLock), Boolean.TRUE.equals(entityLock));
    }

    private static String safe(Object val) {
        if (val == null) return "null";
        if (val instanceof Set<?> set) return set.stream().map(String::valueOf).sorted().collect(Collectors.joining(", ", "[", "]"));
        return val.toString().strip();
    }

    private static Object normalize(Object value) {
        if (value instanceof String s) return s.strip();
        return value;
    }

    private static Set<String> toNameSet(Set<?> entities) {
        if (entities == null) return null;
        return entities.stream()
                .map(e -> {
                    try {
                        return (String) e.getClass().getMethod("getName").invoke(e);
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(String::strip)
                .collect(Collectors.toSet());
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}