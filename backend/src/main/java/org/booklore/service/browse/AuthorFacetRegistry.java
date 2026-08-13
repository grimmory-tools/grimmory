package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.FacetLogic;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.enums.ReadStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthorFacetRegistry {

    private static final Set<String> NAMES = Set.of(
            "has_asin", "has_photo", "has_description", "read_status", "book_count", "library", "genre", "language");

    private final AuthorVisibleBooks visibleBooks;
    private final AuthorPhotoIndex photoIndex;

    public boolean has(String facetName) {
        return NAMES.contains(facetName);
    }

    public Set<String> facetNames() {
        return NAMES;
    }

    public Specification<AuthorEntity> toSpecification(String facetName, List<String> values, FacetLogic logic, BrowseScope scope) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String value : values) {
                predicates.add(valuePredicate(facetName, value, root, query, cb, scope));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return switch (logic == null ? FacetLogic.AND : logic) {
                case AND -> cb.and(predicates.toArray(Predicate[]::new));
                case OR -> cb.or(predicates.toArray(Predicate[]::new));
                case NOT -> cb.not(cb.or(predicates.toArray(Predicate[]::new)));
            };
        };
    }

    public Predicate valuePredicate(String facetName, String value, Root<AuthorEntity> root,
                                    CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        return switch (facetName) {
            case "has_asin" -> booleanPredicate(cb, hasAsin(root, cb), value, facetName);
            case "has_photo" -> booleanPredicate(cb, hasPhoto(root, cb), value, facetName);
            case "has_description" -> booleanPredicate(cb, hasDescription(root, cb), value, facetName);
            case "read_status" -> readStatus(root, query, cb, scope, value);
            case "book_count" -> bookCountRange(root, query, cb, scope, value);
            case "library" -> library(root, query, cb, scope, value);
            case "genre" -> genre(root, query, cb, scope, value);
            case "language" -> language(root, query, cb, scope, value);
            default -> throw ApiError.INVALID_FACET.createException("Unknown facet: " + facetName);
        };
    }

    private Predicate hasAsin(Root<AuthorEntity> root, CriteriaBuilder cb) {
        return cb.and(cb.isNotNull(root.get("asin")), cb.notEqual(root.get("asin"), ""));
    }

    private Predicate hasDescription(Root<AuthorEntity> root, CriteriaBuilder cb) {
        return cb.and(cb.isNotNull(root.get("description")), cb.notEqual(root.get("description"), ""));
    }

    private Predicate hasPhoto(Root<AuthorEntity> root, CriteriaBuilder cb) {
        Set<Long> ids = photoIndex.authorIdsWithPhoto();
        return ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }

    private Predicate booleanPredicate(CriteriaBuilder cb, Predicate truePredicate, String value, String facetName) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> truePredicate;
            case "false" -> truePredicate.not();
            default -> throw ApiError.INVALID_FACET.createException(
                    "Facet " + facetName + " accepts true or false, got: " + value);
        };
    }

    private Predicate readStatus(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                 BrowseScope scope, String value) {
        ReadStatus status;
        try {
            status = ReadStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiError.INVALID_FACET.createException(
                    "Invalid read_status value: " + value + ". Valid values: " + List.of(ReadStatus.values()));
        }
        return visibleBooks.hasBookWithReadStatus(root.get("id"), query, cb, scope, status);
    }

    private Predicate bookCountRange(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                     BrowseScope scope, String value) {
        long[] bounds = parseRange(value);
        Subquery<Long> count = visibleBooks.bookCount(root.get("id"), query, cb, scope);
        if (bounds[0] >= 0 && bounds[1] >= 0) {
            return cb.and(cb.greaterThanOrEqualTo(count, bounds[0]), cb.lessThanOrEqualTo(count, bounds[1]));
        }
        if (bounds[0] >= 0) {
            return cb.greaterThanOrEqualTo(count, bounds[0]);
        }
        return cb.lessThanOrEqualTo(count, bounds[1]);
    }

    private static long[] parseRange(String value) {
        String range = value.trim();
        try {
            if (!range.contains("-")) {
                long exact = Long.parseLong(range);
                requireNonNegative(exact, value);
                return new long[]{exact, exact};
            }
            int dash = range.indexOf('-');
            String left = range.substring(0, dash).trim();
            String right = range.substring(dash + 1).trim();
            long min = left.isEmpty() ? -1 : Long.parseLong(left);
            long max = right.isEmpty() ? -1 : Long.parseLong(right);
            if (min < 0 && max < 0) {
                throw ApiError.INVALID_FACET.createException("Facet book_count range needs at least one bound: " + value);
            }
            if (!left.isEmpty()) {
                requireNonNegative(min, value);
            }
            if (!right.isEmpty()) {
                requireNonNegative(max, value);
            }
            if (min >= 0 && max >= 0 && min > max) {
                throw ApiError.INVALID_FACET.createException("Facet book_count range is inverted: " + value);
            }
            return new long[]{min, max};
        } catch (NumberFormatException e) {
            throw ApiError.INVALID_FACET.createException(
                    "Facet book_count must be a count or range (e.g. 4, 2-10, 5-, -3), got: " + value);
        }
    }

    private static void requireNonNegative(long bound, String value) {
        if (bound < 0) {
            throw ApiError.INVALID_FACET.createException("Facet book_count bounds must be non-negative: " + value);
        }
    }

    private Predicate library(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                              BrowseScope scope, String value) {
        long libraryId;
        try {
            libraryId = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw ApiError.INVALID_FACET.createException("Facet library expects a library id, got: " + value);
        }
        return visibleBooks.exists(root.get("id"), query, cb, scope,
                (book, metadata, q, criteria) -> criteria.equal(book.get("library").get("id"), libraryId));
    }

    private Predicate genre(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                            BrowseScope scope, String value) {
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        return visibleBooks.exists(root.get("id"), query, cb, scope,
                (book, metadata, q, criteria) -> {
                    Join<?, ?> category = metadata.join("categories", JoinType.INNER);
                    return criteria.equal(criteria.lower(category.get("name")), lowered);
                });
    }

    private Predicate language(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                               BrowseScope scope, String value) {
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        return visibleBooks.exists(root.get("id"), query, cb, scope,
                (book, metadata, q, criteria) -> criteria.equal(criteria.lower(metadata.get("language")), lowered));
    }
}
