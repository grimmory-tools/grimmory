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
            Join<BookMetadataEntity, AuthorEntity> authorsJoin = metadataJoin.join("authors", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(metadataJoin.get("title")), pattern));
            predicates.add(cb.like(cb.lower(metadataJoin.get("seriesName")), pattern));
            predicates.add(cb.like(cb.lower(authorsJoin.get("name")), pattern));

            query.distinct(true);

            return cb.or(predicates.toArray(new Predicate[0]));
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
        return (root, query, cb) -> {
            if (authorName == null || authorName.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Join<BookMetadataEntity, AuthorEntity> authorsJoin = metadataJoin.join("authors", JoinType.INNER);
            query.distinct(true);
            return cb.equal(cb.lower(authorsJoin.get("name")), authorName.toLowerCase().trim());
        };
    }

    /**
     * Filter books by language code (case-insensitive).
     */
    public static Specification<BookEntity> withLanguage(String language) {
        return (root, query, cb) -> {
            if (language == null || language.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            return cb.equal(cb.lower(metadataJoin.get("language")), language.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> inSeries(String seriesName) {
        return (root, query, cb) -> {
            if (seriesName == null || seriesName.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            return cb.equal(cb.lower(metadataJoin.get("seriesName")), seriesName.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> withCategory(String categoryName) {
        return (root, query, cb) -> {
            if (categoryName == null || categoryName.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Join<BookMetadataEntity, CategoryEntity> categoriesJoin = metadataJoin.join("categories", JoinType.INNER);
            query.distinct(true);
            return cb.equal(cb.lower(categoriesJoin.get("name")), categoryName.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> withPublisher(String publisher) {
        return (root, query, cb) -> {
            if (publisher == null || publisher.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            return cb.equal(cb.lower(metadataJoin.get("publisher")), publisher.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> withTag(String tagName) {
        return (root, query, cb) -> {
            if (tagName == null || tagName.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Join<BookMetadataEntity, TagEntity> tagsJoin = metadataJoin.join("tags", JoinType.INNER);
            query.distinct(true);
            return cb.equal(cb.lower(tagsJoin.get("name")), tagName.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> withMood(String moodName) {
        return (root, query, cb) -> {
            if (moodName == null || moodName.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Join<BookMetadataEntity, MoodEntity> moodsJoin = metadataJoin.join("moods", JoinType.INNER);
            query.distinct(true);
            return cb.equal(cb.lower(moodsJoin.get("name")), moodName.toLowerCase().trim());
        };
    }

    public static Specification<BookEntity> withNarrator(String narrator) {
        return (root, query, cb) -> {
            if (narrator == null || narrator.trim().isEmpty()) {
                return cb.conjunction();
            }
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            return cb.equal(cb.lower(metadataJoin.get("narrator")), narrator.toLowerCase().trim());
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
