package org.booklore.service.browse;

import lombok.RequiredArgsConstructor;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.browse.FacetLogic;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.UserContentRestrictionRepository;
import org.booklore.security.policy.ContentRestrictionSpecification;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookFilterSpecifications {

    private final BookFacetRegistry facetRegistry;
    private final UserContentRestrictionRepository restrictionRepository;

    public Specification<BookEntity> base(String query, Map<String, List<String>> facets, FacetLogic facetLogic,
                                          Long userId, boolean isAdmin, Set<Long> libraryIds, String omitFacet) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(AppBookSpecification.notDeleted());
        if (!isAdmin) {
            specs.add(inLibraries(libraryIds));
            specs.add(ContentRestrictionSpecification.from(restrictionRepository.findByUserId(userId)));
        }
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
            specs.add(facetRegistry.toSpecification(entry.getKey(), entry.getValue(), facetLogic, userId));
        }
        return AppBookSpecification.combine(specs.toArray(Specification[]::new));
    }

    private static Specification<BookEntity> inLibraries(Set<Long> libraryIds) {
        return (root, query, cb) -> libraryIds.isEmpty()
                ? cb.disjunction()
                : root.get("library").get("id").in(libraryIds);
    }

    public static Set<Long> libraryIds(BookLoreUser user) {
        if (user.getAssignedLibraries() == null) {
            return Set.of();
        }
        return user.getAssignedLibraries().stream().map(Library::getId).collect(Collectors.toSet());
    }

    public static Map<String, List<String>> parseFacets(List<String> facet) {
        Map<String, List<String>> facets = new LinkedHashMap<>();
        if (facet == null) {
            return facets;
        }
        for (String entry : facet) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                throw ApiError.INVALID_FACET.createException("Facet must be in key:value form: " + entry);
            }
            facets.computeIfAbsent(entry.substring(0, colon), k -> new ArrayList<>()).add(entry.substring(colon + 1));
        }
        return facets;
    }
}
