package org.grimmory.app.specification;

import org.booklore.exception.APIException;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.ReadStatus;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class AppBookSpecification {

    private AppBookSpecification() {
    }

    private static List<Integer> parseIntList(List<String> values, String paramName) {
        List<String> invalid = new ArrayList<>();
        List<Integer> result = values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        invalid.add(s.trim());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        if (!invalid.isEmpty()) {
            throw new APIException("Invalid " + paramName + " values: " + invalid, HttpStatus.BAD_REQUEST);
        }
        return result;
    }

    private static List<Long> parseLongList(List<String> values, String paramName) {
        List<String> invalid = new ArrayList<>();
        List<Long> result = values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> {
                    try {
                        return Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        invalid.add(s.trim());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        if (!invalid.isEmpty()) {
            throw new APIException("Invalid " + paramName + " values: " + invalid, HttpStatus.BAD_REQUEST);
        }
        return result;
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

    /**
     * Filter books that have any progress for a specific user.
     * If `optional` is true, allow books without user progress to be included
     * but omit other user's progress.
     */
    public static Specification<BookEntity> withProgress(Long userId, boolean optional) {
        return (root, query, cb) -> {
            if (userId == null) {
                return cb.conjunction();
            }

            Join<BookEntity, UserBookProgressEntity> progressJoin = root.join("userBookProgress", JoinType.LEFT);
            progressJoin = progressJoin.on(cb.equal(progressJoin.get("user").get("id"), userId));

            if (optional) {
                return cb.conjunction();
            }

            return cb.equal(progressJoin.get("user").get("id"), userId);
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
     * Filter books by multiple file types with mode support.
     * OR  = books with at least one file of ANY listed type
     * AND = books with files of ALL listed types
     * NOT = books with NONE of the listed file types
     */
    public static Specification<BookEntity> withFileTypes(List<String> fileTypes, String mode) {
        return (root, query, cb) -> {
            List<String> unknown = new ArrayList<>();
            List<BookFileType> parsed = fileTypes.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> {
                        String trimmed = s.trim().toUpperCase();
                        try {
                            return BookFileType.valueOf(trimmed);
                        } catch (IllegalArgumentException e) {
                            unknown.add(s.trim());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (!unknown.isEmpty()) {
                throw new APIException("Invalid fileType values: " + unknown + ". Valid values: " + List.of(BookFileType.values()), HttpStatus.BAD_REQUEST);
            }
            if (parsed.isEmpty()) return cb.conjunction();

            if ("and".equals(mode)) {
                List<Predicate> predicates = new ArrayList<>();
                for (BookFileType ft : parsed) {
                    Subquery<Long> sub = query.subquery(Long.class);
                    Root<BookFileEntity> bfRoot = sub.from(BookFileEntity.class);
                    sub.select(bfRoot.get("book").get("id"))
                            .where(cb.equal(bfRoot.get("bookType"), ft));
                    predicates.add(root.get("id").in(sub));
                }
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookFileEntity> bfRoot = sub.from(BookFileEntity.class);
            sub.select(bfRoot.get("book").get("id"))
                    .where(bfRoot.get("bookType").in(parsed));

            if ("not".equals(mode)) {
                return cb.not(root.get("id").in(sub));
            }
            return root.get("id").in(sub);
        };
    }

    /**
     * Filter books by multiple read statuses with mode support (per-user).
     * OR  = books with ANY of the listed read statuses
     * AND = impossible for a single-value field, treated as OR
     * NOT = books with NONE of the listed read statuses
     */
    public static Specification<BookEntity> withReadStatuses(List<String> statuses, Long userId, String mode) {
        return (root, query, cb) -> {
            if (userId == null) return cb.conjunction();
            List<String> unknown = new ArrayList<>();
            List<ReadStatus> parsed = statuses.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> {
                        String trimmed = s.trim().toUpperCase();
                        try {
                            return ReadStatus.valueOf(trimmed);
                        } catch (IllegalArgumentException e) {
                            unknown.add(s.trim());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
            if (!unknown.isEmpty()) {
                throw new APIException("Invalid status values: " + unknown + ". Valid values: " + List.of(ReadStatus.values()), HttpStatus.BAD_REQUEST);
            }
            if (parsed.isEmpty()) return cb.conjunction();

            boolean hasUnset = parsed.contains(ReadStatus.UNSET);
            List<ReadStatus> realStatuses = parsed.stream().filter(s -> s != ReadStatus.UNSET).toList();

            Subquery<Long> sub = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = sub.from(UserBookProgressEntity.class);
            sub.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            realStatuses.isEmpty() ? cb.disjunction() : progressRoot.get("readStatus").in(realStatuses)
                    );

            Predicate isReal = root.get("id").in(sub);
            
            // UNSET means either no progress entry OR entry with UNSET status
            Subquery<Long> anyEntrySub = query.subquery(Long.class);
            Root<UserBookProgressEntity> anyEntryRoot = anyEntrySub.from(UserBookProgressEntity.class);
            anyEntrySub.select(anyEntryRoot.get("book").get("id"))
                    .where(cb.equal(anyEntryRoot.get("user").get("id"), userId));
            
            Predicate noEntry = cb.not(root.get("id").in(anyEntrySub));
            
            Subquery<Long> unsetEntrySub = query.subquery(Long.class);
            Root<UserBookProgressEntity> unsetEntryRoot = unsetEntrySub.from(UserBookProgressEntity.class);
            unsetEntrySub.select(unsetEntryRoot.get("book").get("id"))
                    .where(
                            cb.equal(unsetEntryRoot.get("user").get("id"), userId),
                            cb.equal(unsetEntryRoot.get("readStatus"), ReadStatus.UNSET)
                    );
            Predicate hasUnsetEntry = root.get("id").in(unsetEntrySub);
            
            Predicate isUnset = cb.or(noEntry, hasUnsetEntry);

            Predicate combined;
            if (hasUnset && !realStatuses.isEmpty()) {
                combined = cb.or(isReal, isUnset);
            } else if (hasUnset) {
                combined = isUnset;
            } else {
                combined = isReal;
            }

            return "not".equals(mode) ? cb.not(combined) : combined;
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

    public static Specification<BookEntity> withAgeRatings(List<String> rangeIds, String mode) {
        return (root, query, cb) -> {
            List<Integer> ids = parseIntList(rangeIds, "ageRating");
            if (ids.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> ageRating = metadataJoin.get("ageRating");
            
            // Age rating ranges from frontend config (mirrored here for performance)
            // 0: [0, 6), 6: [6, 10), 10: [10, 13), 13: [13, 16), 16: [16, 18), 18: [18, 21), 21: [21, inf)
            List<Predicate> predicates = new ArrayList<>();
            for (Integer id : ids) {
                predicates.add(switch (id) {
                    case 0 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 0), cb.lessThan(ageRating, 6));
                    case 6 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 6), cb.lessThan(ageRating, 10));
                    case 10 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 10), cb.lessThan(ageRating, 13));
                    case 13 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 13), cb.lessThan(ageRating, 16));
                    case 16 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 16), cb.lessThan(ageRating, 18));
                    case 18 -> cb.and(cb.greaterThanOrEqualTo(ageRating, 18), cb.lessThan(ageRating, 21));
                    case 21 -> cb.greaterThanOrEqualTo(ageRating, 21);
                    default -> throw new APIException("Invalid ageRating bucket ID: " + id, HttpStatus.BAD_REQUEST);
                });
            }
            Predicate combined = cb.or(predicates.toArray(Predicate[]::new));
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withContentRatings(List<String> values, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(values);
            if (cleaned.isEmpty()) return cb.conjunction();
            return buildMetadataFieldSpec(root, query, cb, cleaned, mode, "contentRating");
        };
    }

    public static Specification<BookEntity> withMatchScores(List<String> rangeIds, String mode) {
        return (root, query, cb) -> {
            List<Integer> ids = parseIntList(rangeIds, "matchScore");
            if (ids.isEmpty()) return cb.conjunction();
            Expression<Float> score = root.get("metadataMatchScore");
            
            List<Predicate> predicates = new ArrayList<>();
            for (Integer id : ids) {
                predicates.add(switch (id) {
                    case 0 -> cb.greaterThanOrEqualTo(score, 0.95f);
                    case 1 -> cb.and(cb.greaterThanOrEqualTo(score, 0.90f), cb.lessThan(score, 0.95f));
                    case 2 -> cb.and(cb.greaterThanOrEqualTo(score, 0.80f), cb.lessThan(score, 0.90f));
                    case 3 -> cb.and(cb.greaterThanOrEqualTo(score, 0.70f), cb.lessThan(score, 0.80f));
                    case 4 -> cb.and(cb.greaterThanOrEqualTo(score, 0.50f), cb.lessThan(score, 0.70f));
                    case 5 -> cb.and(cb.greaterThanOrEqualTo(score, 0.30f), cb.lessThan(score, 0.50f));
                    case 6 -> cb.and(cb.greaterThanOrEqualTo(score, 0.00f), cb.lessThan(score, 0.30f));
                    default -> throw new APIException("Invalid matchScore bucket ID: " + id, HttpStatus.BAD_REQUEST);
                });
            }
            Predicate combined = cb.or(predicates.toArray(Predicate[]::new));
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withPublishedYears(List<String> years, String mode) {
        return (root, query, cb) -> {
            if (years.isEmpty()) return cb.conjunction();
            List<Integer> parsedYears = parseIntList(years, "publishedDate");
            if (parsedYears.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> yearExpr = cb.function("YEAR", Integer.class, metadataJoin.get("publishedDate"));
            Predicate combined = yearExpr.in(parsedYears);
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withFileSizes(List<String> rangeIds, String mode) {
        return (root, query, cb) -> {
            List<Integer> ids = parseIntList(rangeIds, "fileSize");
            if (ids.isEmpty()) return cb.conjunction();
            
            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookEntity> subRoot = sub.correlate(root);
            Join<BookEntity, BookFileEntity> bfJoin = subRoot.join("bookFiles", JoinType.INNER);
            
            Subquery<Long> minIdSub = query.subquery(Long.class);
            Root<BookEntity> minIdRoot = minIdSub.correlate(root);
            Join<BookEntity, BookFileEntity> minIdJoin = minIdRoot.join("bookFiles", JoinType.INNER);
            minIdSub.select(cb.min(minIdJoin.get("id")))
                    .where(cb.equal(minIdJoin.get("isBookFormat"), true));

            Expression<Long> size = bfJoin.get("fileSizeKb");
            List<Predicate> predicates = new ArrayList<>();
            for (Integer id : ids) {
                predicates.add(switch (id) {
                    case 0 -> cb.and(cb.greaterThanOrEqualTo(size, 0L), cb.lessThan(size, 1024L));
                    case 1 -> cb.and(cb.greaterThanOrEqualTo(size, 1024L), cb.lessThan(size, 10240L));
                    case 2 -> cb.and(cb.greaterThanOrEqualTo(size, 10240L), cb.lessThan(size, 51200L));
                    case 3 -> cb.and(cb.greaterThanOrEqualTo(size, 51200L), cb.lessThan(size, 102400L));
                    case 4 -> cb.and(cb.greaterThanOrEqualTo(size, 102400L), cb.lessThan(size, 512000L));
                    case 5 -> cb.and(cb.greaterThanOrEqualTo(size, 512000L), cb.lessThan(size, 1048576L));
                    case 6 -> cb.and(cb.greaterThanOrEqualTo(size, 1048576L), cb.lessThan(size, 2097152L));
                    case 7 -> cb.greaterThanOrEqualTo(size, 2097152L);
                    default -> throw new APIException("Invalid fileSize bucket ID: " + id, HttpStatus.BAD_REQUEST);
                });
            }
            
            sub.select(cb.literal(1L))
               .where(cb.equal(bfJoin.get("id"), minIdSub),
                      cb.or(predicates.toArray(Predicate[]::new)));

            return "not".equals(mode) ? cb.not(cb.exists(sub)) : cb.exists(sub);
        };
    }

    public static Specification<BookEntity> withPersonalRatings(List<String> values, Long userId, String mode) {
        return (root, query, cb) -> {
            if (userId == null || values.isEmpty()) return cb.conjunction();
            List<Integer> parsed = parseIntList(values, "personalRating");
            if (parsed.isEmpty()) return cb.conjunction();
            Subquery<Long> sub = query.subquery(Long.class);
            Root<UserBookProgressEntity> progressRoot = sub.from(UserBookProgressEntity.class);
            sub.select(progressRoot.get("book").get("id"))
                    .where(
                            cb.equal(progressRoot.get("user").get("id"), userId),
                            progressRoot.get("personalRating").in(parsed)
                    );
            return "not".equals(mode) ? cb.not(root.get("id").in(sub)) : root.get("id").in(sub);
        };
    }

    public static Specification<BookEntity> withAmazonRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "amazonRating");
    }

    public static Specification<BookEntity> withGoodreadsRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "goodreadsRating");
    }

    public static Specification<BookEntity> withHardcoverRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "hardcoverRating");
    }

    public static Specification<BookEntity> withLubimyczytacRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "lubimyczytacRating");
    }

    public static Specification<BookEntity> withRanobedbRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "ranobedbRating");
    }

    public static Specification<BookEntity> withAudibleRatings(List<String> rangeIds, String mode) {
        return buildRatingRangeSpec(rangeIds, mode, "audibleRating");
    }

    private static Specification<BookEntity> buildRatingRangeSpec(
            List<String> rangeIds, String mode, String fieldName) {
        return (root, query, cb) -> {
            List<Integer> ids = parseIntList(rangeIds, fieldName);
            if (ids.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Double> rating = metadataJoin.get(fieldName);
            List<Predicate> predicates = new ArrayList<>();
            for (Integer id : ids) {
                predicates.add(switch (id) {
                    case 5 -> cb.greaterThanOrEqualTo(rating, 4.5);
                    case 4 -> cb.and(cb.greaterThanOrEqualTo(rating, 4.0), cb.lessThan(rating, 4.5));
                    case 3 -> cb.and(cb.greaterThanOrEqualTo(rating, 3.0), cb.lessThan(rating, 4.0));
                    case 2 -> cb.and(cb.greaterThanOrEqualTo(rating, 2.0), cb.lessThan(rating, 3.0));
                    case 1 -> cb.and(cb.greaterThanOrEqualTo(rating, 1.0), cb.lessThan(rating, 2.0));
                    case 0 -> cb.lessThan(rating, 1.0);
                    default -> throw new APIException("Invalid " + fieldName + " bucket ID: " + id, HttpStatus.BAD_REQUEST);
                });
            }
            Predicate combined = cb.or(predicates.toArray(Predicate[]::new));
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withPageCounts(List<String> rangeIds, String mode) {
        return (root, query, cb) -> {
            List<Integer> ids = parseIntList(rangeIds, "pageCount");
            if (ids.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> count = metadataJoin.get("pageCount");
            List<Predicate> predicates = new ArrayList<>();
            for (Integer id : ids) {
                predicates.add(switch (id) {
                    case 0 -> cb.lessThan(count, 50);
                    case 1 -> cb.and(cb.greaterThanOrEqualTo(count, 50), cb.lessThan(count, 100));
                    case 2 -> cb.and(cb.greaterThanOrEqualTo(count, 100), cb.lessThan(count, 200));
                    case 3 -> cb.and(cb.greaterThanOrEqualTo(count, 200), cb.lessThan(count, 400));
                    case 4 -> cb.and(cb.greaterThanOrEqualTo(count, 400), cb.lessThan(count, 600));
                    case 5 -> cb.and(cb.greaterThanOrEqualTo(count, 600), cb.lessThan(count, 1000));
                    case 6 -> cb.greaterThanOrEqualTo(count, 1000);
                    default -> throw new APIException("Invalid pageCount bucket ID: " + id, HttpStatus.BAD_REQUEST);
                });
            }
            Predicate combined = cb.or(predicates.toArray(Predicate[]::new));
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withShelfStatus(List<String> values, String mode) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(values);
            if (cleaned.isEmpty()) return cb.conjunction();
            List<String> invalid = cleaned.stream()
                    .filter(v -> !v.equals("shelved") && !v.equals("unshelved"))
                    .toList();
            if (!invalid.isEmpty()) {
                throw new APIException("Invalid shelfStatus values: " + invalid
                        + ". Valid values: [shelved, unshelved]", HttpStatus.BAD_REQUEST);
            }
            boolean wantShelved = cleaned.contains("shelved");
            boolean wantUnshelved = cleaned.contains("unshelved");
            if (wantShelved && wantUnshelved) return cb.conjunction();
            Predicate hasShelves = cb.isNotEmpty(root.get("shelves"));
            Predicate combined = wantShelved ? hasShelves : cb.not(hasShelves);
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withComicCharacters(List<String> values, String mode) {
        return buildComicCollectionSpec(values, mode, "characters");
    }

    public static Specification<BookEntity> withComicTeams(List<String> values, String mode) {
        return buildComicCollectionSpec(values, mode, "teams");
    }

    public static Specification<BookEntity> withComicLocations(List<String> values, String mode) {
        return buildComicCollectionSpec(values, mode, "locations");
    }

    private static Specification<BookEntity> buildComicCollectionSpec(
            List<String> values, String mode, String collectionAttr) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(values);
            if (cleaned.isEmpty()) return cb.conjunction();
            
            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookMetadataEntity> metaRoot = sub.from(BookMetadataEntity.class);
            Join<?, ?> comicJoin = metaRoot.join("comicMetadata", JoinType.INNER);
            Join<?, ?> collJoin = comicJoin.join(collectionAttr, JoinType.INNER);
            sub.select(cb.literal(1L))
                    .where(
                            cb.equal(metaRoot.get("id"), root.get("id")),
                            cb.lower(collJoin.get("name")).in(cleaned)
                    );
            return "not".equals(mode) ? cb.not(cb.exists(sub)) : cb.exists(sub);
        };
    }

    public static Specification<BookEntity> inShelves(List<String> shelfIds, String mode) {
        return (root, query, cb) -> {
            if (shelfIds.isEmpty()) return cb.conjunction();
            List<Long> ids = parseLongList(shelfIds, "shelf");
            if (ids.isEmpty()) return cb.conjunction();
            
            if ("and".equals(mode)) {
                List<Predicate> predicates = new ArrayList<>();
                for (Long id : ids) {
                    Subquery<Long> sub = query.subquery(Long.class);
                    Root<BookEntity> subRoot = sub.correlate(root);
                    Join<BookEntity, ShelfEntity> subShelves = subRoot.join("shelves", JoinType.INNER);
                    sub.select(cb.literal(1L)).where(cb.equal(subShelves.get("id"), id));
                    predicates.add(cb.exists(sub));
                }
                return cb.and(predicates.toArray(Predicate[]::new));
            }

            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookEntity> subRoot = sub.correlate(root);
            Join<BookEntity, ShelfEntity> subShelves = subRoot.join("shelves", JoinType.INNER);
            sub.select(cb.literal(1L)).where(subShelves.get("id").in(ids));
            return "not".equals(mode) ? cb.not(cb.exists(sub)) : cb.exists(sub);
        };
    }

    public static Specification<BookEntity> inLibraries(List<String> libraryIds, String mode) {
        return (root, query, cb) -> {
            if (libraryIds.isEmpty()) return cb.conjunction();
            List<Long> ids = parseLongList(libraryIds, "library");
            if (ids.isEmpty()) return cb.conjunction();
            
            if ("and".equals(mode)) {
                if (ids.size() > 1) return cb.disjunction(); // A book can't be in multiple libraries
                return cb.equal(root.get("library").get("id"), ids.getFirst());
            }

            Predicate combined = root.get("library").get("id").in(ids);
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    public static Specification<BookEntity> withComicCreators(List<String> values, String mode) {
        return (root, query, cb) -> {
            if (values == null || values.isEmpty()) return cb.conjunction();

            // value is formatted as "name:role" from frontend
            List<Predicate> predicates = new ArrayList<>();
            for (String val : values) {
                String[] parts = val.split(":");
                String name = parts[0].trim().toLowerCase();
                String roleName = parts.length > 1 ? parts[1].trim() : null;

                Subquery<Long> sub = query.subquery(Long.class);
                Root<ComicCreatorMappingEntity> mappingRoot = sub.from(ComicCreatorMappingEntity.class);
                Join<?, ?> creatorJoin = mappingRoot.join("creator", JoinType.INNER);
                Join<?, ?> comicJoin = mappingRoot.join("comicMetadata", JoinType.INNER);

                List<Predicate> where = new ArrayList<>();
                where.add(cb.equal(comicJoin.get("bookId"), root.get("id")));
                where.add(cb.equal(cb.lower(creatorJoin.get("name")), name));

                if (roleName != null) {
                    ComicCreatorRole role = parseCreatorRole(roleName);
                    where.add(cb.equal(mappingRoot.get("role"), role));
                }

                sub.select(cb.literal(1L)).where(where.toArray(Predicate[]::new));
                predicates.add(cb.exists(sub));
            }

            Predicate combined = "and".equals(mode)
                    ? cb.and(predicates.toArray(Predicate[]::new))
                    : cb.or(predicates.toArray(Predicate[]::new));
            return "not".equals(mode) ? cb.not(combined) : combined;
        };
    }

    private static ComicCreatorRole parseCreatorRole(String roleName) {
        return switch (roleName.toLowerCase()) {
            case "penciller" -> ComicCreatorRole.PENCILLER;
            case "inker" -> ComicCreatorRole.INKER;
            case "colorist" -> ComicCreatorRole.COLORIST;
            case "letterer" -> ComicCreatorRole.LETTERER;
            case "coverartist" -> ComicCreatorRole.COVER_ARTIST;
            case "editor" -> ComicCreatorRole.EDITOR;
            default -> throw new APIException("Invalid comic creator role: " + roleName, HttpStatus.BAD_REQUEST);
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
            return cb.and(predicates.toArray(Predicate[]::new));
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
