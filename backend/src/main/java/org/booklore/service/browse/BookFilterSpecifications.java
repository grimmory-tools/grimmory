package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.browse.FacetLogic;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class BookFilterSpecifications {

    private final BookFacetRegistry facetRegistry;

    public Specification<BookEntity> base(String query, Map<String, List<String>> facets, FacetLogic facetLogic,
                                          BrowseScope scope, String omitFacet) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(scope.visibleBooks());
        if (query != null && !query.isBlank()) {
            specs.add(BookSearchSpecification.matching(query));
        }
        for (Map.Entry<String, List<String>> entry : facets.entrySet()) {
            if (Objects.equals(entry.getKey(), omitFacet)) {
                continue;
            }
            if (!facetRegistry.has(entry.getKey())) {
                throw ApiError.INVALID_FACET.createException("Unknown facet: " + entry.getKey());
            }
            specs.add(facetRegistry.toSpecification(entry.getKey(), entry.getValue(), facetLogic, scope.userId()));
        }
        return AppBookSpecification.combine(specs.toArray(Specification[]::new));
    }
}
