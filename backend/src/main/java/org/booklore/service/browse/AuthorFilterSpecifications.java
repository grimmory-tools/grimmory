package org.booklore.service.browse;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorFilterSpecifications {

    private final AuthorFacetRegistry facetRegistry;
    private final AuthorVisibleBooks visibleBooks;

    public Specification<AuthorEntity> base(String query, Map<String, List<String>> facets, BrowseScope scope, String omitFacet) {
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
            char mark = entry.getKey().charAt(0);
            String key = mark == '+' || mark == '-' ? entry.getKey().substring(1) : entry.getKey();
            if (!facetRegistry.has(key)) {
                throw ApiError.INVALID_FACET.createException("Unknown facet: " + entry.getKey());
            }
            if (mark == '+') {
                entry.getValue().forEach(value -> specs.add(facetRegistry.matching(key, List.of(value), scope)));
            } else if (mark == '-') {
                specs.add(excluding(facetRegistry.matching(key, entry.getValue(), scope)));
            } else if (!key.equals(omitFacet)) {
                specs.add(facetRegistry.matching(key, entry.getValue(), scope));
            }
        }
        return Specification.allOf(specs);
    }

    private static Specification<AuthorEntity> excluding(Specification<AuthorEntity> matching) {
        return (root, query, cb) -> {
            Subquery<Long> matches = query.subquery(Long.class);
            Root<AuthorEntity> author = matches.from(AuthorEntity.class);
            matches.select(author.get("id")).where(matching.toPredicate(author, query, cb));
            return cb.not(root.get("id").in(matches));
        };
    }
}
