package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.BrowsePage;
import org.booklore.browse.SortTerm;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookQueryService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class BookSeriesCollapse {

    private static final Comparator<Member> REPRESENTATIVE_ORDER = Comparator
            .comparing(Member::seriesNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Member::id);

    private final BookSortRegistry sortRegistry;
    private final BookQueryService bookQueryService;
    private final BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    record CollapsedPage(List<Book> content, long totalGroups, BrowsePage.Grouping grouping) {
    }

    private record Group(Long anchorId, boolean series) {
    }

    private record Member(Long id, Float seriesNumber) {
    }

    CollapsedPage page(Specification<BookEntity> matching, List<SortTerm> sortTerms, long offset, int limit, Long userId, Integer randomSeed) {
        List<Group> groups = pageGroups(matching, sortTerms, offset, limit, userId, randomSeed);
        Map<Long, List<Member>> membersByAnchor = seriesMembers(groups, matching);

        List<Long> bookIds = new ArrayList<>();
        Map<Long, List<Long>> membersByBookId = new LinkedHashMap<>();
        for (Group group : groups) {
            List<Member> members = membersByAnchor.get(group.anchorId());
            if (members == null) {
                bookIds.add(group.anchorId());
                continue;
            }
            Long representativeId = Collections.min(members, REPRESENTATIVE_ORDER).id();
            bookIds.add(representativeId);
            membersByBookId.put(representativeId, members.stream().map(Member::id).sorted().toList());
        }

        Map<Long, BookEntity> entities = bookQueryService.findAllWithMetadataByIds(new HashSet<>(bookIds)).stream()
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));
        List<BookEntity> ordered = bookIds.stream().map(entities::get).toList();
        List<Book> books = bookQueryService.mapEntitiesToDto(ordered, false, userId);
        var grouping = new BrowsePage.Grouping(bookRepository.count(matching), membersByBookId);
        return new CollapsedPage(books, countGroups(matching), grouping);
    }

    private List<Group> pageGroups(Specification<BookEntity> matching, List<SortTerm> sortTerms, long offset, int limit, Long userId, Integer randomSeed) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        Root<BookEntity> root = query.from(BookEntity.class);
        var metadata = root.join("metadata", JoinType.LEFT);
        Expression<String> series = cb.nullif(metadata.<String>get("seriesName"), "");

        query.where(matching.toPredicate(root, query, cb));
        List<Order> orders = new ArrayList<>();
        for (SortTerm term : sortTerms) {
            List<Order> termOrders = "title".equals(term.key())
                    ? List.of(order(cb, cb.coalesce(series, metadata.<String>get("title")), term.descending()))
                    : sortRegistry.registry().toOrders(List.of(term), root, query, cb, userId, randomSeed);
            termOrders.forEach(order -> orders.add(groupOrder(cb, order)));
        }
        query.multiselect(cb.min(root.<Long>get("id")), cb.greatest(series))
                .groupBy(standaloneId(root, cb, series), series)
                .orderBy(orders);

        return entityManager.createQuery(query)
                .setFirstResult((int) offset)
                .setMaxResults(limit)
                .getResultList().stream()
                .map(row -> new Group(row.get(0, Long.class), row.get(1) != null))
                .toList();
    }

    private Map<Long, List<Member>> seriesMembers(List<Group> groups, Specification<BookEntity> matching) {
        List<Long> anchorIds = groups.stream().filter(Group::series).map(Group::anchorId).toList();
        if (anchorIds.isEmpty()) {
            return Map.of();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        Root<BookEntity> anchor = query.from(BookEntity.class);
        Root<BookEntity> member = query.from(BookEntity.class);
        var memberMetadata = member.join("metadata");
        query.multiselect(anchor.get("id"), member.get("id"), memberMetadata.get("seriesNumber"))
                .distinct(true)
                .where(anchor.get("id").in(anchorIds),
                        cb.equal(anchor.join("metadata").get("seriesName"), memberMetadata.get("seriesName")),
                        matching.toPredicate(member, query, cb));

        Map<Long, List<Member>> members = new HashMap<>();
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            members.computeIfAbsent(row.get(0, Long.class), id -> new ArrayList<>())
                    .add(new Member(row.get(1, Long.class), row.get(2, Float.class)));
        }
        return members;
    }

    private long countGroups(Specification<BookEntity> matching) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Long.class);
        Root<BookEntity> root = query.from(BookEntity.class);
        Expression<String> series = cb.nullif(root.join("metadata", JoinType.LEFT).<String>get("seriesName"), "");

        query.where(matching.toPredicate(root, query, cb));
        query.select(cb.sum(cb.countDistinct(standaloneId(root, cb, series)), cb.countDistinct(series)));
        return entityManager.createQuery(query).getSingleResult();
    }

    private static Expression<Long> standaloneId(Root<BookEntity> root, CriteriaBuilder cb, Expression<String> series) {
        return cb.<Long>selectCase()
                .when(cb.isNull(series), root.<Long>get("id"))
                .otherwise(cb.nullLiteral(Long.class));
    }

    private static Order order(CriteriaBuilder cb, Expression<?> value, boolean descending) {
        return descending ? cb.desc(value) : cb.asc(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Order groupOrder(CriteriaBuilder cb, Order order) {
        Expression value = order.getExpression();
        return order.isAscending() ? cb.asc(cb.least(value)) : cb.desc(cb.greatest(value));
    }
}
