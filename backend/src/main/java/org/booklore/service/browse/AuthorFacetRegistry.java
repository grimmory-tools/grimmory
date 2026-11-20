package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.booklore.browse.NumericRange;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.booklore.service.browse.FacetDef.choices;
import static org.booklore.service.browse.FacetDef.names;
import static org.booklore.service.browse.FacetDef.number;
import static org.booklore.service.browse.FacetDef.values;

@Component
public class AuthorFacetRegistry {

    private final AuthorVisibleBooks visibleBooks;
    private final AuthorPhotoIndex photoIndex;
    private final FacetRegistry<AuthorEntity> registry;

    public AuthorFacetRegistry(AuthorVisibleBooks visibleBooks, AuthorPhotoIndex photoIndex) {
        this.visibleBooks = visibleBooks;
        this.photoIndex = photoIndex;
        this.registry = build();
    }

    public FacetRegistry<AuthorEntity> registry() {
        return registry;
    }

    private FacetRegistry<AuthorEntity> build() {
        return new FacetRegistry<>(List.of(
                choices("has_asin", "ASIN", anyValue(
                        (root, query, cb, scope, value) -> booleanPredicate(hasAsin(root, cb), value, "has_asin")), "true", "false"),
                choices("has_photo", "Photo", anyValue(
                        (root, query, cb, scope, value) -> booleanPredicate(hasPhoto(root, cb), value, "has_photo")), "true", "false"),
                choices("has_description", "Description", anyValue(
                        (root, query, cb, scope, value) -> booleanPredicate(hasDescription(root, cb), value, "has_description")), "true", "false"),
                names("genre", "Genre", anyValue(this::genre),
                        (root, query, cb, scope) -> visibleMetadata(root, query, cb, scope).join("categories", JoinType.LEFT).get("name")),
                names("language", "Language", anyValue(this::language),
                        (root, query, cb, scope) -> visibleMetadata(root, query, cb, scope).get("language")),
                values("library", "Library", anyValue(this::library),
                        (root, query, cb, scope) -> visibleMetadata(root, query, cb, scope).join("book").get("library").get("id")),
                values("read_status", "Read Status", anyValue(this::readStatus),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, UserBookProgressEntity> progress =
                                    visibleMetadata(root, query, cb, scope).join("book").join("userBookProgress", JoinType.LEFT);
                            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
                            return cb.<ReadStatus>selectCase()
                                    .when(progress.get("id").isNull(), ReadStatus.UNSET)
                                    .otherwise(progress.get("readStatus"));
                        }),
                number("book_count", "Book Count", this::bookCount,
                        (root, query, cb, scope) -> visibleBooks.bookCount(root.get("id"), query, cb, scope))));
    }

    private Join<AuthorEntity, BookMetadataEntity> visibleMetadata(Root<AuthorEntity> root, CriteriaQuery<?> query,
                                                                  CriteriaBuilder cb, BrowseScope scope) {
        Join<AuthorEntity, BookMetadataEntity> metadata = root.join("bookMetadataEntityList", JoinType.INNER);
        metadata.on(visibleBooks.bookIdVisible(metadata.get("bookId"), query, cb, scope));
        return metadata;
    }

    private static FacetFilter<AuthorEntity> anyValue(ValuePredicate predicate) {
        return (values, scope) -> (root, query, cb) -> values.isEmpty()
                ? cb.conjunction()
                : cb.or(values.stream().map(value -> predicate.apply(root, query, cb, scope, value)).toArray(Predicate[]::new));
    }

    private Specification<AuthorEntity> bookCount(List<String> values, BrowseScope scope) {
        return (root, query, cb) -> {
            List<NumericRange<Long>> ranges = NumericRange.parseAll(values, Long::valueOf, "book_count");
            return ranges.isEmpty()
                    ? cb.conjunction()
                    : NumericRange.anyOf(cb, visibleBooks.bookCount(root.get("id"), query, cb, scope), ranges);
        };
    }

    @FunctionalInterface
    private interface ValuePredicate {
        Predicate apply(Root<AuthorEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope, String value);
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

    private Predicate booleanPredicate(Predicate truePredicate, String value, String facetName) {
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
