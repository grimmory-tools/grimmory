package org.booklore.app.specification;

import jakarta.persistence.criteria.*;
import org.booklore.model.entity.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public class AppAuthorSpecification {

    private AppAuthorSpecification() {
    }

    /**
     * Factory for the book count expression. 
     * Note: This adds a JOIN to the query it is used in.
     */
    public static Expression<Long> bookCountExpression(Root<AuthorEntity> root, CriteriaBuilder cb) {
        Join<AuthorEntity, BookMetadataEntity> bmJoin = getOrCreateJoin(root, "bookMetadataEntityList", JoinType.INNER);
        return cb.countDistinct(bmJoin.get("bookId"));
    }

    public static Specification<AuthorEntity> inLibraries(Collection<Long> libraryIds) {
        return (root, query, cb) -> {
            if (libraryIds == null || libraryIds.isEmpty()) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, BookEntity> book = bm.join("book");

            sq.select(cb.literal(1L))
              .where(
                  cb.isMember(root, bm.<Collection<AuthorEntity>>get("authors")),
                  book.get("library").get("id").in(libraryIds)
              );

            return cb.exists(sq);
        };
    }

    public static Specification<AuthorEntity> inLibrary(Long libraryId) {
        return (root, query, cb) -> {
            if (libraryId == null) {
                return cb.conjunction();
            }
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, BookEntity> book = bm.join("book");

            sq.select(cb.literal(1L))
              .where(
                  cb.isMember(root, bm.<Collection<AuthorEntity>>get("authors")),
                  cb.equal(book.get("library").get("id"), libraryId)
              );

            return cb.exists(sq);
        };
    }

    public static Specification<AuthorEntity> notDeleted() {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, BookEntity> book = bm.join("book");

            sq.select(cb.literal(1L))
              .where(
                  cb.isMember(root, bm.<Collection<AuthorEntity>>get("authors")),
                  cb.or(
                      cb.isNull(book.get("deleted")),
                      cb.equal(book.get("deleted"), false)
                  )
              );

            return cb.exists(sq);
        };
    }

    public static Specification<AuthorEntity> hasDigitalFile() {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, BookEntity> book = bm.join("book");

            sq.select(cb.literal(1L))
              .where(
                  cb.isMember(root, bm.<Collection<AuthorEntity>>get("authors")),
                  cb.isNotEmpty(book.get("bookFiles"))
              );

            return cb.exists(sq);
        };
    }

    public static Specification<AuthorEntity> hasPhoto(boolean hasPhoto) {
        return (root, query, cb) -> cb.equal(root.get("hasPhoto"), hasPhoto);
    }

    public static Specification<AuthorEntity> searchText(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
            return cb.like(cb.lower(root.get("name")), pattern);
        };
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
}
