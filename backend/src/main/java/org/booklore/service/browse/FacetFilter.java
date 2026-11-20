package org.booklore.service.browse;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;

@FunctionalInterface
public interface FacetFilter<E> {

    Specification<E> matching(List<String> values, BrowseScope scope);
}
