package org.booklore.app.specification;

import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AppBookSpecification {

    private AppBookSpecification() {
    }

    @SuppressWarnings("unchecked")
    private static <X, Y> Join<X, Y> getOrCreateJoin(From<?, X> from, String attribute, JoinType joinType) {
        for (Join<X, ?> join : from.getJoins()) {
            if (join.getAttribute().getName().equals(attribute) && join.getJoinType() == joinType) {
                return (Join<X, Y>) join;
            }
        }
        return from.join(attribute, joinType);
    }

    public static Specification<BookEntity> inLibraries(Collection<Long> libraryIds) {
        return (root, query, cb) -> {
            if (libraryIds == null || libraryIds.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("library").get("id").in(libraryIds);
        };
    }

    public static Specification<BookEntity> inLibrary(Long libraryId) {
        return (root, query, cb) -> {
            if (libraryId == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("library").get("id"), libraryId);
        };
    }

    public static Specification<BookEntity> inShelf(Long shelfId) {
        return (root, query, cb) -> {
            if (shelfId == null) {
                return cb.conjunction();
            }
            Join<BookEntity, ShelfEntity> shelvesJoin = root.join("shelves", JoinType.INNER);
            return cb.equal(shelvesJoin.get("id"), shelfId);
        };
    }

    public static Specification<BookEntity> withReadStatus(ReadStatus status, Long userId) {
        return (root, query, cb) -> {
            if (status == null || userId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);
            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            cb.equal(progressRoot.get("readStatus"), status)
                    );
            return root.get("id").in(subquery);
        };
    }

    public static Specification<BookEntity> inProgress(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);
            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            progressRoot.get("readStatus").in(ReadStatus.READING, ReadStatus.RE_READING)
                    );
            return root.get("id").in(subquery);
        };
    }

    public static Specification<BookEntity> addedWithinDays(int days) {
        return (root, query, cb) -> {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            return cb.greaterThanOrEqualTo(root.get("addedOn"), cutoff);
        };
    }

    public static Specification<BookEntity> searchText(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";

            Join<BookEntity, BookMetadataEntity> metadataJoin = root.join("metadata", JoinType.LEFT);

            // Use EXISTS subquery for author search to avoid DISTINCT and cartesian products
            Subquery<Long> authorSubquery = query.subquery(Long.class);
            Root<BookMetadataEntity> metaRoot = authorSubquery.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, AuthorEntity> authorJoin = metaRoot.join("authors", JoinType.INNER);
            authorSubquery.select(cb.literal(1L))
                    .where(
                            cb.equal(metaRoot.get("id"), root.get("id")),
                            cb.like(cb.lower(authorJoin.get("name")), pattern)
                    );

            return cb.or(
                    cb.like(cb.lower(metadataJoin.get("title")), pattern),
                    cb.like(cb.lower(metadataJoin.get("seriesName")), pattern),
                    cb.exists(authorSubquery)
            );
        };
    }

    public static Specification<BookEntity> notDeleted() {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("deleted")),
                cb.equal(root.get("deleted"), false)
        );
    }

    public static Specification<BookEntity> hasScannedOn() {
        return (root, query, cb) -> cb.isNotNull(root.get("scannedOn"));
    }

    public static Specification<BookEntity> hasDigitalFile() {
        return (root, query, cb) -> cb.isNotEmpty(root.get("bookFiles"));
    }

    public static Specification<BookEntity> hasAudiobookFile() {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BookFileEntity> bookFileRoot = subquery.from(BookFileEntity.class);
            subquery.select(bookFileRoot.get("book").get("id"))
                    .where(cb.equal(bookFileRoot.get("bookType"), BookFileType.AUDIOBOOK));
            return root.get("id").in(subquery);
        };
    }

    public static Specification<BookEntity> hasNonAudiobookFile() {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BookFileEntity> bookFileRoot = subquery.from(BookFileEntity.class);
            subquery.select(bookFileRoot.get("book").get("id"))
                    .where(cb.notEqual(bookFileRoot.get("bookType"), BookFileType.AUDIOBOOK));
            return root.get("id").in(subquery);
        };
    }

    /**
     * Filter books that have at least one file of the given type.
     */
    public static Specification<BookEntity> withFileType(BookFileType fileType) {
        return (root, query, cb) -> {
            if (fileType == null) {
                return cb.conjunction();
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BookFileEntity> bookFileRoot = subquery.from(BookFileEntity.class);
            subquery.select(bookFileRoot.get("book").get("id"))
                    .where(cb.equal(bookFileRoot.get("bookType"), fileType));
            return root.get("id").in(subquery);
        };
    }

    /**
     * Filter books where the user's personal rating is >= minRating.
     */
    public static Specification<BookEntity> withMinRating(int minRating, Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);
            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            cb.greaterThanOrEqualTo(progressRoot.get("personalRating"), minRating)
                    );
            return root.get("id").in(subquery);
        };
    }

    /**
     * Filter books where the user's personal rating is <= maxRating.
     * Use maxRating=0 to find unrated books.
     */
    public static Specification<BookEntity> withMaxRating(int maxRating, Long userId) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = subquery.from(UserBookProgressEntity.class);

            if (maxRating == 0) {
                // Unrated: books with no progress entry or null personalRating
                Subquery<Long> ratedSubquery = query.subquery(Long.class);
                Root<UserBookProgressEntity> ratedRoot = ratedSubquery.from(UserBookProgressEntity.class);
                ratedSubquery.select(ratedRoot.get("book").get("id"))
                        .where(
                                cb.equal(ratedRoot.get("user").get("id"), userId),
                                cb.isNotNull(ratedRoot.get("personalRating"))
                        );
                return cb.not(root.get("id").in(ratedSubquery));
            }

            subquery.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            cb.lessThanOrEqualTo(progressRoot.get("personalRating"), maxRating)
                    );
            return root.get("id").in(subquery);
        };
    }

    /**
     * Filter books by author name (case-insensitive exact match).
     */
    public static Specification<BookEntity> withAuthor(String authorName) {
        return withAuthors(authorName == null ? List.of() : List.of(authorName), "or");
    }

    /**
     * Filter books by multiple author names with mode support.
     * OR  = books with ANY of the authors
     * AND = books with ALL of the authors
     * NOT = books with NONE of the authors
     */
    public static Specification<BookEntity> withAuthors(List<String> authorNames, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(authorNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildManyToManySpec(root, query, cb, cleaned, mode,
                    "metadata", "authors", "name");
        };
    }

    /**
     * Filter books by language code (case-insensitive).
     */
    public static Specification<BookEntity> withLanguage(String language) {
        return withLanguages(language == null ? List.of() : List.of(language), "or");
    }

    /**
     * Filter books by multiple language codes with mode support.
     */
    public static Specification<BookEntity> withLanguages(List<String> languages, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(languages);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, mode, "language");
        };
    }

    public static Specification<BookEntity> inSeries(String seriesName) {
        return inSeriesMulti(seriesName == null ? List.of() : List.of(seriesName), "or");
    }

    /**
     * Filter books by multiple series names with mode support.
     */
    public static Specification<BookEntity> inSeriesMulti(List<String> seriesNames, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(seriesNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, mode, "seriesName");
        };
    }

    public static Specification<BookEntity> withCategory(String categoryName) {
        return withCategories(categoryName == null ? List.of() : List.of(categoryName), "or");
    }

    /**
     * Filter books by multiple categories with mode support.
     */
    public static Specification<BookEntity> withCategories(List<String> categoryNames, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(categoryNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildManyToManySpec(root, query, cb, cleaned, mode,
                    "metadata", "categories", "name");
        };
    }

    public static Specification<BookEntity> withPublisher(String publisher) {
        return withPublishers(publisher == null ? List.of() : List.of(publisher), "or");
    }

    /**
     * Filter books by multiple publishers with mode support.
     */
    public static Specification<BookEntity> withPublishers(List<String> publishers, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(publishers);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, mode, "publisher");
        };
    }

    public static Specification<BookEntity> withTag(String tagName) {
        return withTags(tagName == null ? List.of() : List.of(tagName), "or");
    }

    /**
     * Filter books by multiple tags with mode support.
     */
    public static Specification<BookEntity> withTags(List<String> tagNames, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(tagNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildManyToManySpec(root, query, cb, cleaned, mode,
                    "metadata", "tags", "name");
        };
    }

    public static Specification<BookEntity> withMood(String moodName) {
        return withMoods(moodName == null ? List.of() : List.of(moodName), "or");
    }

    /**
     * Filter books by multiple moods with mode support.
     */
    public static Specification<BookEntity> withMoods(List<String> moodNames, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(moodNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildManyToManySpec(root, query, cb, cleaned, mode,
                    "metadata", "moods", "name");
        };
    }

    public static Specification<BookEntity> withNarrator(String narrator) {
        return withNarrators(narrator == null ? List.of() : List.of(narrator), "or");
    }

    /**
     * Filter books by multiple narrators with mode support.
     */
    public static Specification<BookEntity> withNarrators(List<String> narrators, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(narrators);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, mode, "narrator");
        };
    }

    public static Specification<BookEntity> unshelved() {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BookEntity> subRoot = subquery.correlate(root);
            Join<BookEntity, ShelfEntity> shelvesJoin = subRoot.join("shelves", JoinType.INNER);
            subquery.select(cb.literal(1L));
            return cb.not(cb.exists(subquery));
        };
    }
    private static List<String> cleanLowerCase(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase())
                .toList();
    }

    /**
     * Builds a specification for a scalar field on BookMetadataEntity (language, seriesName, publisher, narrator).
     * OR  = metadata field IN (values)
     * AND = impossible for scalar fields with multiple values (treated as OR since a scalar can only be one value)
     * NOT = metadata field NOT IN (values)
     */
    private static Predicate buildMetadataFieldSpec(
            Root<BookEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<String> values, String mode, String fieldName) {

        Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
        Expression<String> fieldExpr = cb.lower(metadataJoin.get(fieldName));

        if ("not".equals(mode)) {
            return fieldExpr.in(values).not();
        }
        // Both "or" and "and" use IN for scalar fields (a single field can only match one value)
        return fieldExpr.in(values);
    }

    /**
     * Builds a specification for a many-to-many collection (authors, categories, tags, moods).
     * Uses EXISTS subqueries to avoid DISTINCT and cartesian product issues.
     *
     * OR  = book has at least one related entity whose name is IN (values)
     * AND = book has ALL of the named entities (one EXISTS per value)
     * NOT = book has NONE of the named entities
     */
    private static Predicate buildManyToManySpec(
            Root<BookEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<String> values, String mode,
            String metadataAttr, String collectionAttr, String nameAttr) {

        if ("and".equals(mode)) {
            // AND: book must have ALL values one EXISTS subquery per value
            List<Predicate> predicates = new ArrayList<>();
            for (String value : values) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<BookMetadataEntity> metaRoot = sub.from(BookMetadataEntity.class);
                Join<?, ?> collJoin = metaRoot.join(collectionAttr, JoinType.INNER);
                sub.select(cb.literal(1L))
                        .where(
                                cb.equal(metaRoot.get("id"), root.get("id")),
                                cb.equal(cb.lower(collJoin.get(nameAttr)), value)
                        );
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }

        // OR or NOT: single EXISTS subquery with IN clause
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookMetadataEntity> metaRoot = sub.from(BookMetadataEntity.class);
        Join<?, ?> collJoin = metaRoot.join(collectionAttr, JoinType.INNER);
        sub.select(cb.literal(1L))
                .where(
                        cb.equal(metaRoot.get("id"), root.get("id")),
                        cb.lower(collJoin.get(nameAttr)).in(values)
                );

        if ("not".equals(mode)) {
            return cb.not(cb.exists(sub));
        }
        return cb.exists(sub);
    }

    @SafeVarargs
    public static Specification<BookEntity> combine(Specification<BookEntity>... specs) {
        Specification<BookEntity> result = (root, query, cb) -> cb.conjunction();
        for (Specification<BookEntity> spec : specs) {
            if (spec != null) {
                result = result.and(spec);
            }
        }
        return result;
    }
}
