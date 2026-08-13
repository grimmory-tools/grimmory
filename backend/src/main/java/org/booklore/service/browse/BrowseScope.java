package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record BrowseScope(
        Long userId,
        boolean isAdmin,
        Set<Long> libraryIds,
        Specification<BookEntity> contentRestriction
) {

    public Predicate visibleBook(Root<BookEntity> book, CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.or(cb.isNull(book.get("deleted")), cb.equal(book.get("deleted"), false)));
        if (!isAdmin) {
            predicates.add(libraryIds.isEmpty()
                    ? cb.disjunction()
                    : book.get("library").get("id").in(libraryIds));
            if (contentRestriction != null) {
                Predicate restriction = contentRestriction.toPredicate(book, query, cb);
                if (restriction != null) {
                    predicates.add(restriction);
                }
            }
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    public Specification<BookEntity> visibleBooks() {
        return this::visibleBook;
    }
}
