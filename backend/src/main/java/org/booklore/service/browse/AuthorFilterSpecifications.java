package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.browse.FacetLogic;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AuthorFilterSpecifications {

    private final AuthorFacetRegistry facetRegistry;
    private final AuthorVisibleBooks visibleBooks;

    public Specification<AuthorEntity> base(String query, Map<String, List<String>> facets, FacetLogic facetLogic,
                                            BrowseScope scope, String omitFacet) {
        List<Specification<AuthorEntity>> specs = new ArrayList<>();
        if (!scope.isAdmin()) {
            specs.add((root, cq, cb) -> visibleBooks.exists(root.get("id"), cq, cb, scope));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            specs.add((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("asin")), pattern)));
        }
        for (Map.Entry<String, List<String>> entry : facets.entrySet()) {
            if (Objects.equals(entry.getKey(), omitFacet)) {
                continue;
            }
            if (!facetRegistry.has(entry.getKey())) {
                throw ApiError.INVALID_FACET.createException("Unknown facet: " + entry.getKey());
            }
            specs.add(facetRegistry.toSpecification(entry.getKey(), entry.getValue(), facetLogic, scope));
        }
        return Specification.allOf(specs);
    }
}
