package org.booklore.service.browse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.ParamsHash;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorFacetService {

    private static final String PAGE_PATH = "/api/v1/authors/page";
    private static final String FACET_PATH = "/api/v1/authors/facets";
    private static final int MAX_VALUES = 100;

    private static final List<ValueFacetDef> VALUE_FACETS = List.of(
            new ValueFacetDef("matched", "Matched", List.of("true", "false")),
            new ValueFacetDef("has_photo", "Photo", List.of("true", "false")),
            new ValueFacetDef("has_description", "Description", List.of("true", "false")));

    private static final List<GroupByFacetDef> GROUP_BY_FACETS = List.of(
            new GroupByFacetDef("genre", "Genre", metadata -> metadata.join("categories", JoinType.LEFT).get("name")),
            new GroupByFacetDef("language", "Language", metadata -> metadata.get("language")));

    private final AuthenticationService authenticationService;
    private final AuthorFilterSpecifications filterSpecifications;
    private final AuthorFacetRegistry facetRegistry;
    private final AuthorSortRegistry sortRegistry;
    private final BrowseScopeFactory scopeFactory;
    private final AuthorVisibleBooks visibleBooks;
    private final EntityManager entityManager;

    private final Cache<String, FacetGroupsResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(200)
            .build();

    public FacetGroupsResponse getFacets(List<String> facet, String facetLogicParam, String query) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BrowseScope scope = scopeFactory.from(user);

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);

        String cacheKey = scope.userId() + ":" + ParamsHash.compute(query, facets, facetLogic);
        return cache.get(cacheKey, key -> {
            String preserved = BrowseParams.preserved(facet, facetLogicParam, query);
            FacetResponseBuilder builder = new FacetResponseBuilder(PAGE_PATH, FACET_PATH, preserved, facet);
            List<FacetGroup> groups = new ArrayList<>();
            groups.add(builder.sortGroup(sortRegistry.registry(scope).keys()));
            for (ValueFacetDef def : VALUE_FACETS) {
                Specification<AuthorEntity> base = filterSpecifications.base(query, facets, facetLogic, scope, def.key());
                groups.add(builder.group(def.key(), def.title(), valueCounts(def, base, scope)));
            }
            for (GroupByFacetDef def : GROUP_BY_FACETS) {
                Specification<AuthorEntity> base = filterSpecifications.base(query, facets, facetLogic, scope, def.key());
                groups.add(builder.group(def.key(), def.title(), groupByCounts(def, base, scope)));
            }
            return new FacetGroupsResponse(builder.selfLinks(), groups);
        });
    }

    void clearCache() {
        cache.invalidateAll();
    }

    private List<FacetResponseBuilder.FacetCount> valueCounts(ValueFacetDef def, Specification<AuthorEntity> base, BrowseScope scope) {
        List<FacetResponseBuilder.FacetCount> counts = new ArrayList<>();
        for (String value : def.values()) {
            long count = countAuthors(base, def.key(), value, scope);
            if (count > 0) {
                counts.add(new FacetResponseBuilder.FacetCount(value, count));
            }
        }
        counts.sort(Comparator.comparingLong(FacetResponseBuilder.FacetCount::count).reversed()
                .thenComparing(FacetResponseBuilder.FacetCount::value));
        return counts;
    }

    private long countAuthors(Specification<AuthorEntity> base, String facetKey, String value, BrowseScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        cq.select(cb.count(root));
        List<Predicate> predicates = new ArrayList<>();
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            predicates.add(basePredicate);
        }
        predicates.add(facetRegistry.valuePredicate(facetKey, value, root, cq, cb, scope));
        cq.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(cq).getSingleResult();
    }

    private List<FacetResponseBuilder.FacetCount> groupByCounts(GroupByFacetDef def, Specification<AuthorEntity> base, BrowseScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        Join<?, ?> metadata = root.join("bookMetadataEntityList", JoinType.INNER);
        Expression<?> value = def.value().apply(metadata);
        Expression<Long> count = cb.countDistinct(root.get("id"));

        List<Predicate> predicates = new ArrayList<>();
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            predicates.add(basePredicate);
        }
        predicates.add(cb.isNotNull(value));
        predicates.add(visibleBooks.bookIdVisible(metadata.get("bookId"), cq, cb, scope));

        cq.multiselect(value.alias("value"), count.alias("count"));
        cq.where(predicates.toArray(Predicate[]::new));
        cq.groupBy(value);
        cq.orderBy(cb.desc(count), cb.asc(value));

        return entityManager.createQuery(cq).setMaxResults(MAX_VALUES).getResultList().stream()
                .map(tuple -> new FacetResponseBuilder.FacetCount(String.valueOf(tuple.get("value")), ((Number) tuple.get("count")).longValue()))
                .toList();
    }

    private record ValueFacetDef(String key, String title, List<String> values) {
    }

    private record GroupByFacetDef(String key, String title, Function<Join<?, ?>, Expression<?>> value) {
    }
}
