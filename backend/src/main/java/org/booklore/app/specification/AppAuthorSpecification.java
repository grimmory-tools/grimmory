package org.booklore.app.specification;

import jakarta.persistence.criteria.*;
import org.booklore.model.entity.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AppAuthorSpecification {

    private AppAuthorSpecification() {
    }

    /**
     * Factory for the book count expression.
     * Note: This adds a JOIN to the query it is used in and filters by visibility.
     */
    public static Expression<Long> bookCount(CommonAbstractCriteria query, Root<AuthorEntity> root, CriteriaBuilder cb, Collection<Long> libraryIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
        Join<BookMetadataEntity, BookEntity> book = bm.join(BookMetadataEntity_.book);

        sq.select(cb.countDistinct(bm.get(BookMetadataEntity_.bookId)))
          .where(combineVisibilityPredicates(root, bm, book, cb, libraryIds));

        return sq;
    }

    /**
     * Consolidates all visibility-related predicates into a single specification.
     * This avoids the "over-matching" problem where independent EXISTS subqueries
     * could be satisfied by different books of the same author.
     */
    public static Specification<AuthorEntity> visibleTo(Collection<Long> libraryIds) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<BookMetadataEntity> bm = sq.from(BookMetadataEntity.class);
            Join<BookMetadataEntity, BookEntity> book = bm.join(BookMetadataEntity_.book);

            sq.select(cb.literal(1L))
              .where(combineVisibilityPredicates(root, bm, book, cb, libraryIds));

            return cb.exists(sq);
        };
    }

    private static Predicate[] combineVisibilityPredicates(Root<AuthorEntity> authorRoot,
                                                           Root<BookMetadataEntity> bm,
                                                           Join<BookMetadataEntity, BookEntity> book,
                                                           CriteriaBuilder cb,
                                                           Collection<Long> libraryIds) {
        List<Predicate> ps = new ArrayList<>();
        ps.add(cb.isMember(authorRoot, bm.get(BookMetadataEntity_.authors)));
        ps.add(cb.or(
            cb.isNull(book.get(BookEntity_.deleted)),
            cb.equal(book.get(BookEntity_.deleted), false)
        ));
        ps.add(cb.isNotEmpty(book.get(BookEntity_.bookFiles)));
        if (libraryIds != null && !libraryIds.isEmpty()) {
            ps.add(book.get(BookEntity_.library).get(LibraryEntity_.id).in(libraryIds));
        }
        return ps.toArray(new Predicate[0]);
    }

    public static Specification<AuthorEntity> hasPhoto(boolean hasPhoto) {
        return (root, query, cb) -> cb.equal(root.get(AuthorEntity_.hasPhoto), hasPhoto);
    }

    public static Specification<AuthorEntity> searchText(String searchQuery) {
        return (root, query, cb) -> {
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                return cb.conjunction();
            }
            String pattern = "%" + searchQuery.toLowerCase().trim() + "%";
            return cb.like(cb.lower(root.get(AuthorEntity_.name)), pattern);
        };
    }

    @SuppressWarnings("unchecked")
    private static <X, Y> Join<X, Y> getOrCreateJoin(From<?, X> from, jakarta.persistence.metamodel.Attribute<X, ?> attribute, JoinType joinType) {
        String name = attribute.getName();
        for (Join<X, ?> join : from.getJoins()) {
            if (join.getAttribute().getName().equals(name) && join.getJoinType() == joinType) {
                return (Join<X, Y>) join;
            }
        }
        return from.join(name, joinType);
    }
}
