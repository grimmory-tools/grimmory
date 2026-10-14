package org.booklore.service.browse;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.SortRegistry;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record BrowseEntity<E, T>(
        Class<E> type,
        String pagePath,
        String facetPath,
        Function<BrowseScope, Specification<E>> visibility,
        Function<String, Specification<E>> search,
        FacetRegistry<E> facets,
        Function<BrowseScope, SortRegistry<E>> sorts,
        PageFetcher<E, T> pages,
        Function<T, Long> id
) {

    public Specification<E> filter(String query, Map<String, List<String>> selections, FacetLogic facetLogic, BrowseScope scope, String omitFacet) {
        List<Specification<E>> specs = new ArrayList<>();
        specs.add(visibility.apply(scope));
        if (query != null && !query.isBlank()) {
            specs.add(search.apply(query));
        }
        for (Map.Entry<String, List<String>> entry : selections.entrySet()) {
            String key = BrowseParams.unmarked(entry.getKey());
            FacetFilter<E> filter = facets.find(key).filter();
            if (entry.getKey().equals(omitFacet)) {
                continue;
            }
            switch (BrowseParams.logic(entry.getKey(), facetLogic)) {
                case AND -> entry.getValue().forEach(value -> specs.add(filter.matching(List.of(value), scope)));
                case NOT -> specs.add(excluding(filter.matching(entry.getValue(), scope)));
                case OR -> specs.add(filter.matching(entry.getValue(), scope));
            }
        }
        return Specification.allOf(specs);
    }

    private Specification<E> excluding(Specification<E> matching) {
        return (root, query, cb) -> {
            Subquery<Long> matches = query.subquery(Long.class);
            Root<E> entity = matches.from(type);
            matches.select(entity.get("id")).where(matching.toPredicate(entity, query, cb));
            return cb.not(root.get("id").in(matches));
        };
    }
}
