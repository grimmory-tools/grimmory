package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.BrowsePager;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.ParamsHash;
import org.booklore.browse.SortParser;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.util.FileService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorBrowseService {

    private static final String PAGE_PATH = "/api/v1/authors/page";
    private static final String FACET_PATH = "/api/v1/authors/facets";

    private final AuthenticationService authenticationService;
    private final AuthorFilterSpecifications filterSpecifications;
    private final AuthorSortRegistry sortRegistry;
    private final AuthorVisibleBooks visibleBooks;
    private final BrowseScopeFactory scopeFactory;
    private final BrowsePager pager;
    private final FileService fileService;
    private final EntityManager entityManager;

    public BrowsePage<AuthorSummary> browse(String sort, List<String> facet, String facetLogicParam, String query, String cursor, Pageable pageable) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BrowseScope scope = scopeFactory.from(user);

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        String paramsHash = ParamsHash.compute(query, facets, facetLogic);

        BrowsePager.Window window = pager.resolve(sort, cursor, pageable, paramsHash);

        SortRegistry<AuthorEntity> registry = sortRegistry.registry(scope);
        List<SortTerm> sortTerms = SortParser.parse(window.sort(), registry.keys());
        Specification<AuthorEntity> spec = filterSpecifications.base(query, facets, facetLogic, scope, null);

        long total = count(spec);
        List<AuthorEntity> authors = page(spec, registry, sortTerms, scope, window.offset(), window.limit());
        List<AuthorSummary> summaries = toSummaries(authors, scope);

        return pager.assemble(PAGE_PATH, FACET_PATH, BrowseParams.preserved(facet, facetLogicParam, query),
                window, total, summaries);
    }

    public List<Long> findAllIds(String sort, List<String> facet, String facetLogicParam, String query) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BrowseScope scope = scopeFactory.from(user);

        Map<String, List<String>> facets = BrowseParams.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);
        SortRegistry<AuthorEntity> registry = sortRegistry.registry(scope);
        List<SortTerm> sortTerms = SortParser.parse(sort, registry.keys());
        Specification<AuthorEntity> spec = filterSpecifications.base(query, facets, facetLogic, scope, null);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        cq.select(root.get("id"));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(registry.toOrders(sortTerms, root, cq, cb, scope.userId(), null));

        return entityManager.createQuery(cq).getResultList();
    }

    private long count(Specification<AuthorEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        cq.select(cb.count(root));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    private List<AuthorEntity> page(Specification<AuthorEntity> spec, SortRegistry<AuthorEntity> registry,
                                    List<SortTerm> sortTerms, BrowseScope scope, long offset, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuthorEntity> cq = cb.createQuery(AuthorEntity.class);
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(registry.toOrders(sortTerms, root, cq, cb, scope.userId(), null));
        return entityManager.createQuery(cq)
                .setFirstResult((int) offset)
                .setMaxResults(limit)
                .getResultList();
    }

    private List<AuthorSummary> toSummaries(List<AuthorEntity> authors, BrowseScope scope) {
        Map<Long, Long> counts = bookCounts(authors, scope);
        return authors.stream()
                .map(author -> AuthorSummary.builder()
                        .id(author.getId())
                        .name(author.getName())
                        .asin(author.getAsin())
                        .bookCount(counts.getOrDefault(author.getId(), 0L).intValue())
                        .hasPhoto(Files.exists(Paths.get(fileService.getAuthorThumbnailFile(author.getId()))))
                        .build())
                .toList();
    }

    private Map<Long, Long> bookCounts(List<AuthorEntity> authors, BrowseScope scope) {
        if (authors.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = authors.stream().map(AuthorEntity::getId).toList();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<AuthorEntity> root = cq.from(AuthorEntity.class);
        cq.multiselect(root.get("id").alias("id"),
                visibleBooks.bookCount(root.get("id"), cq, cb, scope).alias("count"));
        cq.where(root.get("id").in(ids));

        Map<Long, Long> counts = new HashMap<>();
        for (Tuple tuple : entityManager.createQuery(cq).getResultList()) {
            Number count = (Number) tuple.get("count");
            counts.put((Long) tuple.get("id"), count == null ? 0L : count.longValue());
        }
        return counts;
    }
}
