package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

// Criteria context handed to a SortOrderBuilder for one sort term
// Query is available for sorts that need a subquery
// userId is null for admin/system queries
public record SortContext<E>(
        Root<E> root,
        CriteriaQuery<?> query,
        CriteriaBuilder cb,
        boolean descending,
        Long userId
) {
}
