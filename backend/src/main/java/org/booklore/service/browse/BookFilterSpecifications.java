package org.booklore.service.browse;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.booklore.app.specification.AppBookSpecification;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BookFilterSpecifications {

    private final BookFacetRegistry facetRegistry;

    public Specification<BookEntity> base(
            String query,
            Map<String, List<String>> facets,
            BrowseScope scope,
            String omitFacet
    ) {
        List<Specification<BookEntity>> specs = new ArrayList<>();
        specs.add(scope.visibleBooks());
        if (query != null && !query.isBlank()) {
            specs.add(BookSearchSpecification.matching(query));
        }
        for (Map.Entry<String, List<String>> entry : facets.entrySet()) {
            String key = BrowseParams.unmarked(entry.getKey());
            if (!facetRegistry.has(key)) {
                throw ApiError.INVALID_FACET.createException("Unknown facet: " + entry.getKey());
            }
            if (entry.getKey().startsWith("+")) {
                entry.getValue().forEach(value -> specs.add(facetRegistry.matching(key, List.of(value), scope.userId())));
            } else if (entry.getKey().startsWith("-")) {
                specs.add(excluding(facetRegistry.matching(key, entry.getValue(), scope.userId())));
            } else if (!key.equals(omitFacet)) {
                specs.add(facetRegistry.matching(key, entry.getValue(), scope.userId()));
            }
        }
        return AppBookSpecification.combine(specs.toArray(Specification[]::new));
    }

    private static Specification<BookEntity> excluding(Specification<BookEntity> matching) {
        return (root, query, cb) -> {
            Subquery<Long> matches = query.subquery(Long.class);
            Root<BookEntity> book = matches.from(BookEntity.class);
            matches.select(book.get("id")).where(matching.toPredicate(book, query, cb));
            return cb.not(root.get("id").in(matches));
        };
    }
}
