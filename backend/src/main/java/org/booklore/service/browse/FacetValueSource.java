package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

@FunctionalInterface
public interface FacetValueSource<E> {

    Expression<?> resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope);
}
