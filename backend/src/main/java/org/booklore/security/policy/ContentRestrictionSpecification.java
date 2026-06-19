package org.booklore.security.policy;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ContentRestrictionSpecification {

    private ContentRestrictionSpecification() {
    }

    public static Specification<BookEntity> from(List<UserContentRestrictionEntity> restrictions) {
        if (restrictions == null || restrictions.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }

        Specification<BookEntity> spec = (root, query, cb) -> cb.conjunction();

        for (CollectionType type : CollectionType.values()) {
            Set<String> excluded = values(restrictions, type.restrictionType, ContentRestrictionMode.EXCLUDE);
            if (!excluded.isEmpty()) {
                spec = spec.and(Specification.not(hasAnyCollectionValue(type.attribute, excluded)));
            }
            Set<String> allowed = values(restrictions, type.restrictionType, ContentRestrictionMode.ALLOW_ONLY);
            if (!allowed.isEmpty()) {
                spec = spec.and(hasAnyCollectionValue(type.attribute, allowed));
            }
        }

        Set<String> excludedRatings = values(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.EXCLUDE);
        if (!excludedRatings.isEmpty()) {
            spec = spec.and(Specification.not(hasContentRatingIn(excludedRatings)));
        }
        Set<String> allowedRatings = values(restrictions, ContentRestrictionType.CONTENT_RATING, ContentRestrictionMode.ALLOW_ONLY);
        if (!allowedRatings.isEmpty()) {
            spec = spec.and(hasContentRatingIn(allowedRatings));
        }

        Integer maxAgeRating = maxAgeRating(restrictions);
        if (maxAgeRating != null) {
            spec = spec.and(Specification.not(hasAgeRatingAtLeast(maxAgeRating)));
        }

        return spec;
    }

    private enum CollectionType {
        CATEGORY(ContentRestrictionType.CATEGORY, "categories"),
        TAG(ContentRestrictionType.TAG, "tags"),
        MOOD(ContentRestrictionType.MOOD, "moods");

        final ContentRestrictionType restrictionType;
        final String attribute;

        CollectionType(ContentRestrictionType restrictionType, String attribute) {
            this.restrictionType = restrictionType;
            this.attribute = attribute;
        }
    }

    private static Specification<BookEntity> hasAnyCollectionValue(String collectionAttribute, Set<String> loweredValues) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> m = sq.from(BookMetadataEntity.class);
            Join<Object, Object> value = m.join(collectionAttribute);
            sq.select(m.get("bookId")).where(
                    cb.equal(m.get("bookId"), root.get("id")),
                    cb.lower(value.get("name")).in(loweredValues));
            return cb.exists(sq);
        };
    }

    private static Specification<BookEntity> hasContentRatingIn(Set<String> loweredValues) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> m = sq.from(BookMetadataEntity.class);
            sq.select(m.get("bookId")).where(
                    cb.equal(m.get("bookId"), root.get("id")),
                    cb.lower(m.get("contentRating")).in(loweredValues));
            return cb.exists(sq);
        };
    }

    private static Specification<BookEntity> hasAgeRatingAtLeast(int threshold) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> m = sq.from(BookMetadataEntity.class);
            sq.select(m.get("bookId")).where(
                    cb.equal(m.get("bookId"), root.get("id")),
                    cb.greaterThanOrEqualTo(m.get("ageRating"), threshold));
            return cb.exists(sq);
        };
    }

    private static Set<String> values(List<UserContentRestrictionEntity> restrictions,
                                      ContentRestrictionType type, ContentRestrictionMode mode) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == type && r.getMode() == mode)
                .map(r -> r.getValue().toLowerCase())
                .collect(Collectors.toSet());
    }

    private static Integer maxAgeRating(List<UserContentRestrictionEntity> restrictions) {
        return restrictions.stream()
                .filter(r -> r.getRestrictionType() == ContentRestrictionType.AGE_RATING)
                .filter(r -> r.getMode() == ContentRestrictionMode.EXCLUDE)
                .map(r -> {
                    try {
                        return Integer.parseInt(r.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);
    }
}
