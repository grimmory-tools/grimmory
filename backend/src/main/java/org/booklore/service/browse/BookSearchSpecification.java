package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.springframework.data.jpa.domain.Specification;

public final class BookSearchSpecification {

    private BookSearchSpecification() {
    }

    public static Specification<BookEntity> matching(String query) {
        return (root, criteriaQuery, cb) -> {
            if (query == null || query.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + query.toLowerCase().trim() + "%";
            Join<BookEntity, BookMetadataEntity> metadata = root.join("metadata", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(metadata.get("title")), pattern),
                    cb.like(cb.lower(metadata.get("seriesName")), pattern),
                    cb.like(cb.lower(metadata.get("isbn13")), pattern),
                    cb.like(cb.lower(metadata.get("isbn10")), pattern),
                    cb.like(cb.lower(metadata.get("asin")), pattern),
                    collectionMatches(root, criteriaQuery, cb, "authors", pattern),
                    collectionMatches(root, criteriaQuery, cb, "categories", pattern),
                    collectionMatches(root, criteriaQuery, cb, "tags", pattern)
            );
        };
    }

    private static Predicate collectionMatches(Root<BookEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                               String collectionAttribute, String pattern) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookMetadataEntity> m = sub.from(BookMetadataEntity.class);
        Join<Object, Object> joined = m.join(collectionAttribute, JoinType.INNER);
        sub.select(cb.literal(1L)).where(
                cb.equal(m.get("bookId"), root.get("id")),
                cb.like(cb.lower(joined.get("name")), pattern));
        return cb.exists(sub);
    }
}
