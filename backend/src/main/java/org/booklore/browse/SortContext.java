package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

/**
 * Criteria context passed to a SortOrderBuilder so it can build JPA Orders for one sort term.
 *
 * @param <E>        the entity root type
 * @param root       the query root
 * @param query      the enclosing criteria query, for sorts that need subqueries
 * @param cb         the criteria builder
 * @param descending whether this term sorts descending
 * @param userId     the requesting user, for per-user sorts; null for admin/system queries
 */
public record SortContext<E>(
        Root<E> root,
        CriteriaQuery<?> query,
        CriteriaBuilder cb,
        boolean descending,
        Long userId
) {
}
