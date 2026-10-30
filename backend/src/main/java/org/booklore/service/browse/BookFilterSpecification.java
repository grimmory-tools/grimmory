package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.ComicCreatorMappingEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.model.enums.ReadStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class BookFilterSpecification {

    private static final String PHYSICAL_FILE_TYPE = "PHYSICAL";

    private BookFilterSpecification() {
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

    private record NumericRange<T>(T min, T max, boolean maxExclusive) {
    }

    private static <T extends Comparable<? super T>> List<NumericRange<T>> parseNumericRanges(List<String> values, Function<String, T> parser, String paramName) {
        List<String> invalid = new ArrayList<>();
        List<NumericRange<T>> ranges = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            NumericRange<T> range = parseNumericRange(value.trim(), parser);
            if (range == null) {
                invalid.add(value.trim());
            } else {
                ranges.add(range);
            }
        }
        if (!invalid.isEmpty()) {
            throw new APIException("Invalid " + paramName + " values: " + invalid
                    + ". Expected a number, min..max, min..*, *..max, or min..<max to exclude max. Minimum must not exceed maximum.", HttpStatus.BAD_REQUEST);
        }
        return ranges;
    }

    private static <T extends Comparable<? super T>> NumericRange<T> parseNumericRange(String value, Function<String, T> parser) {
        try {
            int separator = value.indexOf("..");
            if (separator < 0) {
                T exact = parser.apply(value);
                return new NumericRange<>(exact, exact, false);
            }
            String minPart = value.substring(0, separator).trim();
            String maxPart = value.substring(separator + 2).trim();
            boolean maxExclusive = maxPart.startsWith("<");
            if (maxExclusive) {
                maxPart = maxPart.substring(1).trim();
            }
            if (maxPart.equals("*") && (maxExclusive || minPart.equals("*"))) {
                return null;
            }
            T min = minPart.equals("*") ? null : parser.apply(minPart);
            T max = maxPart.equals("*") ? null : parser.apply(maxPart);
            if (min != null && max != null && min.compareTo(max) > 0) {
                return null;
            }
            return new NumericRange<>(min, max, maxExclusive);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static <T extends Comparable<? super T>> Predicate rangesPredicate(
            CriteriaBuilder cb, Expression<T> field, List<NumericRange<T>> ranges) {
        List<Predicate> predicates = new ArrayList<>();
        for (NumericRange<T> range : ranges) {
            if (range.min() != null && range.max() != null) {
                predicates.add(cb.and(
                        cb.greaterThanOrEqualTo(field, range.min()),
                        upperBound(cb, field, range)));
            } else if (range.min() != null) {
                predicates.add(cb.greaterThanOrEqualTo(field, range.min()));
            } else {
                predicates.add(upperBound(cb, field, range));
            }
        }
        return cb.or(predicates.toArray(Predicate[]::new));
    }

    private static <T extends Comparable<? super T>> Predicate upperBound(
            CriteriaBuilder cb, Expression<T> field, NumericRange<T> range) {
        return range.maxExclusive() ? cb.lessThan(field, range.max()) : cb.lessThanOrEqualTo(field, range.max());
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

    public static Specification<BookEntity> hasDigitalFileOrIsPhysical() {
        return (root, query, cb) -> cb.or(
                cb.isNotEmpty(root.get("bookFiles")),
                cb.equal(root.get("isPhysical"), true)
        );
    }

    /**
     * Filter books by multiple file types.
     * PHYSICAL matches the book's physical flag, alongside any digital file types.
     */
    public static Specification<BookEntity> withFileTypes(List<String> fileTypes) {
        if (fileTypes.stream().anyMatch(s -> s != null && PHYSICAL_FILE_TYPE.equalsIgnoreCase(s.trim()))) {
            List<String> digitalTypes = fileTypes.stream()
                    .filter(s -> s != null && !s.isBlank() && !PHYSICAL_FILE_TYPE.equalsIgnoreCase(s.trim()))
                    .toList();
            Specification<BookEntity> physical = (root, query, cb) ->
                    cb.isTrue(root.get("isPhysical"));
            if (digitalTypes.isEmpty()) {
                return physical;
            }
            return physical.or(withFileTypes(digitalTypes));
        }
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
                throw ApiError.GENERIC_BAD_REQUEST.createException("Invalid fileType values: " + unknown + ". Valid values: " + List.of(BookFileType.values()) + ", " + PHYSICAL_FILE_TYPE);
            }
            if (parsed.isEmpty()) return cb.conjunction();

            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookFileEntity> bfRoot = sub.from(BookFileEntity.class);
            sub.select(bfRoot.get("book").get("id"))
                    .where(bfRoot.get("bookType").in(parsed),
                            cb.isTrue(bfRoot.get("isBookFormat")));
            return root.get("id").in(sub);
        };
    }

    /**
     * Filter books by multiple read statuses (per-user).
     */
    public static Specification<BookEntity> withReadStatuses(List<String> statuses, Long userId) {
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

            return combined;
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
     * Filter books by multiple language codes.
     */
    public static Specification<BookEntity> withLanguages(List<String> languages) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(languages);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, "language");
        };
    }

    public static Specification<BookEntity> inSeries(String seriesName) {
        return inSeriesMulti(seriesName == null ? List.of() : List.of(seriesName));
    }

    /**
     * Filter books by multiple series names.
     */
    public static Specification<BookEntity> inSeriesMulti(List<String> seriesNames) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(seriesNames);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, "seriesName");
        };
    }

    /**
     * Filter books by multiple publishers.
     */
    public static Specification<BookEntity> withPublishers(List<String> publishers) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(publishers);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, "publisher");
        };
    }

    public static Specification<BookEntity> withMetadataCollection(
            String collectionAttr, List<String> names, Function<String, List<Long>> idsForName) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(names);
            if (cleaned.isEmpty()) return cb.conjunction();
            return buildManyToManySpec(root, query, cb, cleaned, collectionAttr, idsForName);
        };
    }

    /**
     * Filter books by multiple narrators.
     */
    public static Specification<BookEntity> withNarrators(List<String> narrators) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(narrators);
            if (cleaned.isEmpty()) return cb.conjunction();

            return buildMetadataFieldSpec(root, query, cb, cleaned, "narrator");
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

    public static Specification<BookEntity> withAgeRatings(List<String> values) {
        return (root, query, cb) -> {
            List<NumericRange<Integer>> ranges = parseNumericRanges(values, Integer::valueOf, "ageRating");
            if (ranges.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> ageRating = metadataJoin.get("ageRating");
            Predicate combined = rangesPredicate(cb, ageRating, ranges);
            return combined;
        };
    }

    public static Specification<BookEntity> withContentRatings(List<String> values) {
        return (root, query, cb) -> {
            List<String> cleaned = cleanLowerCase(values);
            if (cleaned.isEmpty()) return cb.conjunction();
            return buildMetadataFieldSpec(root, query, cb, cleaned, "contentRating");
        };
    }

    public static Specification<BookEntity> withMatchScores(List<String> values) {
        return (root, query, cb) -> {
            List<NumericRange<Float>> ranges = parseNumericRanges(values, Float::valueOf, "matchScore");
            if (ranges.isEmpty()) return cb.conjunction();
            Expression<Float> score = root.get("metadataMatchScore");
            Predicate combined = rangesPredicate(cb, score, ranges);
            return combined;
        };
    }

    public static Specification<BookEntity> withPublishedYears(List<String> years) {
        return (root, query, cb) -> {
            List<NumericRange<Integer>> ranges = parseNumericRanges(years, Integer::valueOf, "publishedDate");
            if (ranges.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> yearExpr = cb.function("YEAR", Integer.class, metadataJoin.get("publishedDate"));
            Predicate combined = rangesPredicate(cb, yearExpr, ranges);
            return combined;
        };
    }

    public static Specification<BookEntity> withFileSizes(List<String> values) {
        return (root, query, cb) -> {
            List<NumericRange<Long>> ranges = parseNumericRanges(values, Long::valueOf, "fileSize");
            if (ranges.isEmpty()) return cb.conjunction();

            // Matches against the book's first book-format file.
            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookFileEntity> file = sub.from(BookFileEntity.class);
            Subquery<Long> earlier = sub.subquery(Long.class);
            Root<BookFileEntity> earlierFile = earlier.from(BookFileEntity.class);
            earlier.select(cb.literal(1L)).where(
                    cb.equal(earlierFile.get("book").get("id"), root.get("id")),
                    cb.equal(earlierFile.get("isBookFormat"), true),
                    cb.lessThan(earlierFile.get("id"), file.get("id")));
            sub.select(cb.literal(1L)).where(
                    cb.equal(file.get("book").get("id"), root.get("id")),
                    cb.equal(file.get("isBookFormat"), true),
                    cb.not(cb.exists(earlier)),
                    rangesPredicate(cb, file.get("fileSizeKb"), ranges));

            return cb.exists(sub);
        };
    }

    public static Specification<BookEntity> withPersonalRatings(List<String> values, Long userId) {
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
            return root.get("id").in(sub);
        };
    }

    public static Specification<BookEntity> withAmazonRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "amazonRating");
    }

    public static Specification<BookEntity> withGoodreadsRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "goodreadsRating");
    }

    public static Specification<BookEntity> withHardcoverRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "hardcoverRating");
    }

    public static Specification<BookEntity> withLubimyczytacRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "lubimyczytacRating");
    }

    public static Specification<BookEntity> withRanobedbRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "ranobedbRating");
    }

    public static Specification<BookEntity> withAudibleRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "audibleRating");
    }

    public static Specification<BookEntity> withApplebooksRatings(List<String> rangeIds) {
        return buildRatingRangeSpec(rangeIds, "applebooksRating");
    }

    private static Specification<BookEntity> buildRatingRangeSpec(
            List<String> values, String fieldName) {
        return (root, query, cb) -> {
            List<NumericRange<Double>> ranges = parseNumericRanges(values, Double::valueOf, fieldName);
            if (ranges.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Double> rating = metadataJoin.get(fieldName);
            Predicate combined = rangesPredicate(cb, rating, ranges);
            return combined;
        };
    }

    public static Specification<BookEntity> withPageCounts(List<String> values) {
        return (root, query, cb) -> {
            List<NumericRange<Integer>> ranges = parseNumericRanges(values, Integer::valueOf, "pageCount");
            if (ranges.isEmpty()) return cb.conjunction();
            Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
            Expression<Integer> count = metadataJoin.get("pageCount");
            Predicate combined = rangesPredicate(cb, count, ranges);
            return combined;
        };
    }

    public static Specification<BookEntity> withShelfStatus(List<String> values) {
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
            return combined;
        };
    }

    public static Specification<BookEntity> withComicCollection(
            String collectionAttr, List<String> values, Function<String, List<Long>> idsForName) {
        return withMetadataCollection("comicMetadata." + collectionAttr, values, idsForName);
    }

    public static Specification<BookEntity> inShelves(List<String> shelfIds) {
        return (root, query, cb) -> {
            if (shelfIds.isEmpty()) return cb.conjunction();
            List<Long> ids = parseLongList(shelfIds, "shelf");
            if (ids.isEmpty()) return cb.conjunction();

            Subquery<Long> sub = query.subquery(Long.class);
            Root<BookEntity> subRoot = sub.correlate(root);
            Join<BookEntity, ShelfEntity> subShelves = subRoot.join("shelves", JoinType.INNER);
            sub.select(cb.literal(1L)).where(subShelves.get("id").in(ids));
            return cb.exists(sub);
        };
    }

    public static Specification<BookEntity> inLibraries(List<String> libraryIds) {
        return (root, query, cb) -> {
            if (libraryIds.isEmpty()) return cb.conjunction();
            List<Long> ids = parseLongList(libraryIds, "library");
            if (ids.isEmpty()) return cb.conjunction();

            return root.get("library").get("id").in(ids);
        };
    }

    public static Specification<BookEntity> withComicCreators(
            List<String> values, Function<String, List<Long>> idsForName) {
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
                where.add(creatorJoin.get("id").in(idsForName.apply(name)));

                if (roleName != null) {
                    ComicCreatorRole role = parseCreatorRole(roleName);
                    where.add(cb.equal(mappingRoot.get("role"), role));
                }

                sub.select(cb.literal(1L)).where(where.toArray(Predicate[]::new));
                predicates.add(cb.exists(sub));
            }

            return cb.or(predicates.toArray(Predicate[]::new));
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
     */
    private static Predicate buildMetadataFieldSpec(
            Root<BookEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<String> values, String fieldName) {

        Join<BookEntity, BookMetadataEntity> metadataJoin = getOrCreateJoin(root, "metadata", JoinType.INNER);
        Expression<String> fieldExpr = cb.lower(metadataJoin.get(fieldName));
        return fieldExpr.in(values);
    }

    /**
     * Builds a specification for a many-to-many collection (authors, categories, tags, moods).
     * Uses an EXISTS subquery to avoid DISTINCT and cartesian product issues.
     */
    private static Predicate buildManyToManySpec(
            Root<BookEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            List<String> values,
            String collectionAttr, Function<String, List<Long>> idsForName) {

        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookMetadataEntity> metaRoot = sub.from(BookMetadataEntity.class);
        From<?, ?> collJoin = joinCollection(metaRoot, collectionAttr);
        sub.select(cb.literal(1L))
                .where(
                        cb.equal(metaRoot.get("id"), root.get("id")),
                        collJoin.get("id").in(values.stream()
                                .flatMap(value -> idsForName.apply(value).stream()).distinct().toList())
                );
        return cb.exists(sub);
    }

    private static From<?, ?> joinCollection(From<?, ?> from, String path) {
        for (String attribute : path.split("\\.")) {
            from = from.join(attribute, JoinType.INNER);
        }
        return from;
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
