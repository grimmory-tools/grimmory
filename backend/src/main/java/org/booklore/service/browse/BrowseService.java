package org.booklore.service.browse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.BrowsePager;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.ParamsHash;
import org.booklore.browse.SortParser;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.dto.browse.FacetGroupsResponse.Metadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Transactional(readOnly = true)
public class BrowseService<E, T> {

    private static final int MAX_VALUES = 100;
    private static final char LIKE_ESCAPE = '!';
    private static final Comparator<FacetResponseBuilder.FacetCount> BY_COUNT = Comparator
            .comparingLong(FacetResponseBuilder.FacetCount::count)
            .reversed()
            .thenComparing(FacetResponseBuilder.FacetCount::value);

    private final BrowseEntity<E, T> entity;
    private final BrowseScopeFactory scopeFactory;
    private final BrowsePager pager;
    private final EntityManager entityManager;

    private final Cache<String, FacetGroupsResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(200)
            .build();

    public BrowseService(BrowseEntity<E, T> entity, BrowseScopeFactory scopeFactory, BrowsePager pager, EntityManager entityManager) {
        this.entity = entity;
        this.scopeFactory = scopeFactory;
        this.pager = pager;
        this.entityManager = entityManager;
    }

    public BrowsePage<T> browse(String sort, List<String> facet, String facetLogicParam, String query, String cursor, Pageable pageable) {
        BrowseScope scope = scopeFactory.current();
        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        BrowsePager.Window window = pager.resolve(sort, cursor, pageable, ParamsHash.compute(query, facets, facetLogic));

        SortRegistry<E> sorts = entity.sorts().apply(scope);
        List<SortTerm> sortTerms = SortParser.parse(window.sort(), sorts.keys());
        Specification<E> filter = entity.filter(query, facets, facetLogic, scope, null);

        PageFetcher.FetchedPage<T> fetched = entity.pages().fetch(scope, filter, sorts, sortTerms, window);
        return pager.assemble(entity.pagePath(), entity.facetPath(), BrowseParams.preserved(facet, facetLogicParam, query),
                window, fetched.total(), fetched.items());
    }

    public List<Long> findAllIds(String sort, List<String> facet, String facetLogicParam, String query) {
        BrowseScope scope = scopeFactory.current();
        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        SortRegistry<E> sorts = entity.sorts().apply(scope);
        List<SortTerm> sortTerms = SortParser.parse(sort, sorts.keys());
        Specification<E> filter = entity.filter(query, facets, facetLogic, scope, null);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<E> root = cq.from(entity.type());
        cq.select(root.get("id"));
        Predicate predicate = filter.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        // The `findAllIds` doesn't pass a cursor so it's a random seed every time.
        cq.orderBy(sorts.toOrders(sortTerms, root, cq, cb, scope.userId(), BrowsePager.newRandomSeed()));
        return entityManager.createQuery(cq).getResultList();
    }

    public List<T> findByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        BrowseScope scope = scopeFactory.current();
        Specification<E> filter = entity.filter(null, Map.of(), FacetLogic.AND, scope, null)
                .and((root, query, cb) -> root.get("id").in(ids));
        BrowsePager.Window window = new BrowsePager.Window(0, ids.size(), null, null, null);

        List<T> items = entity.pages().fetch(scope, filter, entity.sorts().apply(scope), List.of(), window).items();
        Map<Long, T> byId = items.stream().collect(Collectors.toMap(entity.id(), item -> item));
        return ids.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    public BrowsePage<T> wrapLegacy(Page<T> page, Pageable pageable) {
        String paramsHash = ParamsHash.compute(null, Map.of(), FacetLogic.OR);

        // Pass a null random seed for the legacy case - we don't actually store a seed value and
        // this is good enough for most use cases.
        BrowsePager.Window window = new BrowsePager.Window(pageable.getOffset(), pageable.getPageSize(), null, paramsHash, null);
        return pager.assemble(entity.pagePath(), entity.facetPath(), "", window, page.getTotalElements(), page.getContent());
    }

    public FacetGroupsResponse getFacets(List<String> facet, String facetLogicParam, String query) {
        BrowseScope scope = scopeFactory.current();
        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);

        String cacheKey = scope.userId() + ":" + ParamsHash.compute(query, facets, facetLogic);
        return cache.get(cacheKey, key -> {
            String preserved = BrowseParams.preserved(facet, facetLogicParam, query);
            FacetResponseBuilder builder = new FacetResponseBuilder(entity.pagePath(), entity.facetPath(), preserved, facet);
            List<FacetGroup> groups = new ArrayList<>();
            groups.add(builder.sortGroup(entity.sorts().apply(scope).keys()));
            for (FacetDef<E> def : entity.facets().all()) {
                Specification<E> base = entity.filter(query, facets, facetLogic, scope, def.key());
                groups.add(new FacetGroup(new Metadata("facet", def.key(), def.title(), hasItems(def, base, scope)), List.of()));
            }
            return new FacetGroupsResponse(builder.selfLinks(), groups);
        });
    }

    public FacetGroupsResponse getFacet(String facetName, List<String> facet, String facetLogicParam, String query, String search, Pageable pageable) {
        BrowseScope scope = scopeFactory.current();
        FacetDef<E> def = entity.facets().find(facetName);
        String term = search == null || search.isBlank() ? null : search.trim();
        if (term != null && !def.searchable()) {
            throw ApiError.INVALID_FACET.createException("Facet cannot be searched: " + facetName);
        }

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        String preserved = BrowseParams.preserved(facet, facetLogicParam, query);
        Specification<E> base = entity.filter(query, facets, facetLogic, scope, def.key());
        String path = entity.facetPath() + "/" + def.key();
        FacetResponseBuilder builder = new FacetResponseBuilder(entity.pagePath(), path, preserved, facet);

        if (!def.searchable()) {
            FacetGroup group = facetGroup(def, base, scope, builder);
            return new FacetGroupsResponse(builder.selfLinks(), List.of(group));
        }

        int page = pageable.getPageNumber();
        int limit = Math.min(pageable.getPageSize(), MAX_VALUES);
        int offset = page * limit;
        // One extra value shows whether a next page exists
        List<FacetResponseBuilder.FacetCount> counts = count(def.groupBy().getFirst(), base, scope, term, offset, limit + 1);
        boolean hasNext = counts.size() > limit;
        FacetGroup group = builder.group(def.key(), def.title(), hasNext ? counts.subList(0, limit) : counts);
        return new FacetGroupsResponse(builder.pagedLinks(term, page, limit, hasNext), List.of(group));
    }

    // Package-private: lets tests reset the shared singleton cache between runs.
    void clearCache() {
        cache.invalidateAll();
    }

    private FacetGroup facetGroup(FacetDef<E> def, Specification<E> base, BrowseScope scope, FacetResponseBuilder builder) {
        if (def.range() != null) {
            return numberGroup(def, base, scope, builder);
        }
        if (def.groupBy().isEmpty() && def.extraValues() == null) {
            return listedGroup(def, base, scope, builder);
        }
        int limit = def.allValues() ? Integer.MAX_VALUE : MAX_VALUES;
        List<FacetResponseBuilder.FacetCount> counts = Stream.concat(
                        def.groupBy().stream().flatMap(source -> count(source, base, scope, null, 0, limit).stream()),
                        extraCounts(def, base, scope).stream())
                .toList();
        if (def.groupBy().size() > 1 || def.extraValues() != null) {
            counts = counts.stream().sorted(BY_COUNT).toList();
        }
        return builder.group(def.key(), def.title(), counts);
    }

    private List<FacetResponseBuilder.FacetCount> extraCounts(FacetDef<E> def, Specification<E> base, BrowseScope scope) {
        if (def.extraValues() == null) {
            return List.of();
        }
        return def.extraValues().apply(scope).entrySet().stream()
                .map(value -> new FacetResponseBuilder.FacetCount(value.getKey(), countMatching(base.and(value.getValue()))))
                .filter(count -> count.count() > 0)
                .toList();
    }

    private boolean hasItems(FacetDef<E> def, Specification<E> base, BrowseScope scope) {
        if (def.range() != null) {
            return anyValue(def.range(), base, scope);
        }
        if (!def.groupBy().isEmpty()) {
            return def.groupBy().stream().anyMatch(source -> anyValue(source, base, scope))
                    || !extraCounts(def, base, scope).isEmpty();
        }
        return anyEntity(base);
    }

    private long countMatching(Specification<E> filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<E> root = cq.from(entity.type());
        Predicate predicate = filter.toPredicate(root, cq, cb);
        cq.select(countRoots(root.get("id"), root, cb));
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    private boolean anyEntity(Specification<E> base) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Integer> cq = cb.createQuery(Integer.class);
        Root<E> root = cq.from(entity.type());
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        cq.select(cb.literal(1));
        if (basePredicate != null) {
            cq.where(basePredicate);
        }
        return !entityManager.createQuery(cq).setMaxResults(1).getResultList().isEmpty();
    }

    private boolean anyValue(FacetValueSource<E> source, Specification<E> base, BrowseScope scope) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Integer> cq = cb.createQuery(Integer.class);
        Root<E> root = cq.from(entity.type());
        Expression<?> value = source.resolve(root, cq, cb, scope);

        List<Predicate> predicates = new ArrayList<>();
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            predicates.add(basePredicate);
        }
        predicates.add(cb.isNotNull(value));

        cq.select(cb.literal(1));
        cq.where(predicates.toArray(Predicate[]::new));
        return !entityManager.createQuery(cq).setMaxResults(1).getResultList().isEmpty();
    }

    private List<FacetResponseBuilder.FacetCount> count(FacetValueSource<E> source, Specification<E> base, BrowseScope scope) {
        return count(source, base, scope, null, 0, MAX_VALUES);
    }

    @SuppressWarnings("unchecked")
    private List<FacetResponseBuilder.FacetCount> count(FacetValueSource<E> source, Specification<E> base, BrowseScope scope, String term, int offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<E> root = cq.from(entity.type());
        Expression<?> value = source.resolve(root, cq, cb, scope);

        List<Predicate> predicates = new ArrayList<>();
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            predicates.add(basePredicate);
        }
        predicates.add(cb.isNotNull(value));
        if (term != null) {
            predicates.add(cb.like(cb.lower((Expression<String>) value), containing(term), LIKE_ESCAPE));
        }

        Expression<Long> count = countRoots(root.get("id"), root, cb);
        cq.multiselect(value.alias("value"), count.alias("count"));
        cq.where(predicates.toArray(Predicate[]::new));
        cq.groupBy(value);
        cq.orderBy(cb.desc(count), cb.asc(value));

        return entityManager.createQuery(cq)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList().stream()
                .map(tuple -> new FacetResponseBuilder.FacetCount(String.valueOf(tuple.get("value")), tuple.get("count", Long.class)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private FacetGroup numberGroup(FacetDef<E> def, Specification<E> base, BrowseScope scope, FacetResponseBuilder builder) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<E> root = cq.from(entity.type());
        Expression<Number> value = (Expression<Number>) def.range().resolve(root, cq, cb, scope);
        List<Expression<Long>> matchingIds = matchingIds(def, root, cq, cb, scope);
        Predicate basePredicate = base.toPredicate(root, cq, cb);

        List<Selection<?>> columns = new ArrayList<>();
        columns.add(cb.min(value).alias("min"));
        columns.add(cb.max(value).alias("max"));
        columns.addAll(listedColumns(def, matchingIds, root, cb));
        Tuple row = singleRow(cq, columns, basePredicate);
        return builder.numberGroup(def.key(), def.title(), listedCounts(def, row), row.get("min", Number.class), row.get("max", Number.class));
    }

    private FacetGroup listedGroup(FacetDef<E> def, Specification<E> base, BrowseScope scope, FacetResponseBuilder builder) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<E> root = cq.from(entity.type());
        List<Expression<Long>> matchingIds = matchingIds(def, root, cq, cb, scope);
        Predicate basePredicate = base.toPredicate(root, cq, cb);

        Tuple row = singleRow(cq, listedColumns(def, matchingIds, root, cb), basePredicate);
        return builder.group(def.key(), def.title(), listedCounts(def, row));
    }

    private List<Expression<Long>> matchingIds(FacetDef<E> def, Root<E> root, CriteriaQuery<?> cq, CriteriaBuilder cb, BrowseScope scope) {
        return def.listed().stream()
                .map(value -> matchingId(def.filter().matching(List.of(value), scope), root, cq, cb))
                .toList();
    }

    private List<Selection<?>> listedColumns(FacetDef<E> def, List<Expression<Long>> matchingIds, Root<E> root, CriteriaBuilder cb) {
        List<Selection<?>> columns = new ArrayList<>();
        for (int i = 0; i < matchingIds.size(); i++) {
            columns.add(countRoots(matchingIds.get(i), root, cb).alias(def.listed().get(i)));
        }
        return columns;
    }

    private List<FacetResponseBuilder.FacetCount> listedCounts(FacetDef<E> def, Tuple row) {
        return def.listed().stream()
                .map(value -> new FacetResponseBuilder.FacetCount(value, row.get(value, Long.class)))
                .toList();
    }

    private Tuple singleRow(CriteriaQuery<Tuple> cq, List<Selection<?>> columns, Predicate basePredicate) {
        cq.multiselect(columns);
        if (basePredicate != null) {
            cq.where(basePredicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    private Expression<Long> matchingId(Specification<E> filter, Root<E> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        return cb.<Long>selectCase()
                .when(filter.toPredicate(root, cq, cb), root.get("id"))
                .otherwise(cb.nullLiteral(Long.class));
    }

    private static String containing(String term) {
        String escaped = term.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private static Expression<Long> countRoots(Expression<Long> id, From<?, ?> root, CriteriaBuilder cb) {
        return hasCollectionJoin(root) ? cb.countDistinct(id) : cb.count(id);
    }

    private static boolean hasCollectionJoin(From<?, ?> from) {
        return from.getJoins().stream().anyMatch(join ->
                join.getAttribute().isCollection() || hasCollectionJoin(join));
    }
}
