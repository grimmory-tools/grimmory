package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePager;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.booklore.model.dto.AuthorSummary;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.util.FileService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorPageFetcher implements PageFetcher<AuthorEntity, AuthorSummary> {

    private final AuthorVisibleBooks visibleBooks;
    private final FileService fileService;
    private final EntityManager entityManager;

    @Override
    public FetchedPage<AuthorSummary> fetch(BrowseScope scope, Specification<AuthorEntity> filter,
                                            SortRegistry<AuthorEntity> sorts, List<SortTerm> sortTerms,
                                            BrowsePager.Window window) {
        long total = count(filter);
        List<AuthorEntity> authors = page(filter, sorts, sortTerms, scope, window.offset(), window.limit());
        return new FetchedPage<>(toSummaries(authors, scope), total);
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
                        .photoUpdatedOn(fileService.getAuthorPhotoUpdatedOn(author.getId()))
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
