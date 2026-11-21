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
import org.booklore.browse.BrowsePager;
import org.booklore.browse.SortRegistry;
import org.booklore.browse.SortTerm;
import org.booklore.model.dto.Book;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.book.BookQueryService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookSeriesCollapsePageFetcher implements PageFetcher<BookEntity, Book> {

    private static final Comparator<Member> REPRESENTATIVE_ORDER = Comparator
            .comparing(Member::seriesNumber, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Member::id);

    private final BookQueryService bookQueryService;
    private final BookPageFetcher bookPageFetcher;

    @PersistenceContext
    private EntityManager entityManager;

    private record Group(Long anchorId, boolean series) {}
    private record Member(Long id, Float seriesNumber) {}
    private record Counts(long groups, long books) {}

    @Override
    public FetchedPage<Book> fetch(BrowseScope scope, Specification<BookEntity> matching, SortRegistry<BookEntity> sorts,
                                   List<SortTerm> sortTerms, BrowsePager.Window window) {
        Counts counts = countGroupsAndBooks(matching);
        List<Group> groups = pageGroups(matching, sorts, sortTerms, (int) window.offset(), window.limit(), scope.userId(), window.randomSeed());
        if (groups.isEmpty()) {
            return new FetchedPage<>(List.of(), counts.groups(), counts.books());
        }
        Map<Long, List<Member>> membersByAnchor = seriesMembers(groups, matching);

        List<Long> bookIds = new ArrayList<>();
        Map<Long, List<Long>> membersByBookId = new HashMap<>();
        for (Group group : groups) {
            if (!group.series()) {
                bookIds.add(group.anchorId());
                continue;
            }
            List<Member> members = membersByAnchor.get(group.anchorId());
            if (members == null || members.isEmpty()) {
                continue;
            }
            Long representativeId = Collections.min(members, REPRESENTATIVE_ORDER).id();
            bookIds.add(representativeId);
            membersByBookId.put(representativeId, members.stream().map(Member::id).sorted().toList());
        }

        Map<Long, BookEntity> entities = bookQueryService.findAllWithMetadataByIds(new HashSet<>(bookIds)).stream()
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));
        List<BookEntity> ordered = bookIds.stream().map(entities::get).filter(Objects::nonNull).toList();
        List<Book> books = bookQueryService.mapEntitiesToDto(ordered, false, scope.userId());
        bookPageFetcher.enrich(books, scope.userId());
        books.forEach(book -> book.setSeriesBookIds(membersByBookId.get(book.getId())));
        return new FetchedPage<>(books, counts.groups(), counts.books());
    }

    private List<Group> pageGroups(Specification<BookEntity> matching, SortRegistry<BookEntity> sorts, List<SortTerm> sortTerms,
                                   int offset, int limit, Long userId, Integer randomSeed) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        Root<BookEntity> root = query.from(BookEntity.class);
        var metadata = root.join("metadata", JoinType.LEFT);
        Expression<String> series = cb.nullif(metadata.<String>get("seriesName"), "");

        query.where(matching.toPredicate(root, query, cb));
        query.distinct(false);
        List<Order> orders = new ArrayList<>();
        for (SortTerm term : sortTerms) {
            switch (term.key()) {
                case "title" -> orders.add(groupOrder(cb, order(cb, cb.coalesce(series, metadata.<String>get("title")), term.descending())));
                case "random" -> orders.add(order(cb, cb.function("RAND", Double.class,
                        cb.sum(cb.literal(randomSeed == null ? 0 : randomSeed), cb.min(root.<Long>get("id")))), term.descending()));
                default -> sorts.toOrders(List.of(term), root, query, cb, userId, randomSeed)
                        .forEach(order -> orders.add(groupOrder(cb, order)));
            }
        }
        query.multiselect(cb.min(root.<Long>get("id")), cb.greatest(series))
                .groupBy(standaloneId(root, cb, series), series)
                .orderBy(orders);

        return entityManager.createQuery(query)
                .setFirstResult(offset)
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

    private Counts countGroupsAndBooks(Specification<BookEntity> matching) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        Root<BookEntity> root = query.from(BookEntity.class);
        Expression<String> series = cb.nullif(root.join("metadata", JoinType.LEFT).<String>get("seriesName"), "");

        query.where(matching.toPredicate(root, query, cb));
        query.distinct(false);
        query.multiselect(cb.sum(cb.countDistinct(standaloneId(root, cb, series)), cb.countDistinct(series)),
                cb.countDistinct(root.get("id")));
        Tuple row = entityManager.createQuery(query).getSingleResult();
        return new Counts(row.get(0, Long.class), row.get(1, Long.class));
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
