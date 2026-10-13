package org.booklore.stats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.booklore.model.enums.ReadStatus;
import org.booklore.stats.StatsResponses.SeriesBar;
import org.booklore.stats.StatsResponses.SeriesPage;
import org.booklore.stats.StatsResponses.SeriesRow;
import org.booklore.stats.StatsResponses.SeriesSummary;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaDerivedRoot;
import org.hibernate.query.criteria.JpaSearchedCase;
import org.hibernate.query.criteria.JpaSelectCriteria;
import org.hibernate.query.criteria.JpaSubQuery;
import org.hibernate.query.criteria.JpaWindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.booklore.model.enums.ReadStatus.ABANDONED;
import static org.booklore.model.enums.ReadStatus.PARTIALLY_READ;
import static org.booklore.model.enums.ReadStatus.PAUSED;
import static org.booklore.model.enums.ReadStatus.READING;
import static org.booklore.model.enums.ReadStatus.RE_READING;
import static org.booklore.model.enums.ReadStatus.UNREAD;
import static org.booklore.model.enums.ReadStatus.UNSET;
import static org.booklore.model.enums.ReadStatus.WONT_READ;

final class SeriesQueries {

    enum Sort {
        PROGRESS, RATING, BOOKS, NAME
    }

    private static final List<String> STATES = List.of("in-progress", "paused", "not-started", "completed", "abandoned");
    private static final List<String> GROUPS = List.of("readBooks", "reading", "partiallyRead", "paused", "abandoned", "wontRead", "unread");
    private static final List<ReadStatus> STILL_TO_READ = List.of(PARTIALLY_READ, PAUSED, UNREAD, UNSET);
    private static final int CHART_SERIES = 8;
    static final int PAGE_SIZE = 10;

    private final EntityManager entityManager;
    private final HibernateCriteriaBuilder cb;
    private final Scope scope;

    SeriesQueries(EntityManager entityManager, Scope scope) {
        this.entityManager = entityManager;
        this.cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();
        this.scope = scope;
    }

    SeriesSummary summary() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        Columns series = columns(query.from(perSeries(query, query)));
        JpaSearchedCase<Integer> rank = cb.selectCase();
        for (int index = 0; index < STATES.size(); index++) {
            rank.when(cb.equal(series.state, STATES.get(index)), index);
        }
        JpaWindow all = cb.createWindow();
        List<Selection<?>> columns = new ArrayList<>(series.selections());
        columns.addAll(List.of(
                cb.count(cb.literal(1), all).alias("total"),
                cb.avg(series.completion, all).alias("averageCompletion"),
                cb.count(series.completion, all).alias("measured"),
                cb.sum(BookSql.flag(cb, cb.equal(series.state, "completed")), all).alias("completed"),
                cb.sum(BookSql.flag(cb, cb.equal(series.state, "in-progress")), all).alias("inProgress"),
                cb.sum(BookSql.flag(cb, cb.equal(series.state, "not-started")), all).alias("notStarted")));
        query.multiselect(columns).orderBy(cb.asc(rank), cb.desc(series.books), cb.asc(series.name));
        List<Tuple> rows = entityManager.createQuery(query).setMaxResults(CHART_SERIES).getResultList();
        if (rows.isEmpty()) {
            return new SeriesSummary(List.of(), 0, null, 0, 0, 0, 0);
        }
        Tuple figures = rows.getFirst();
        return new SeriesSummary(
                rows.stream().map(each -> new SeriesBar(each.get("series", String.class), each.get("books", Long.class),
                        each.get("readBooks", Long.class), each.get("reading", Long.class), each.get("partiallyRead", Long.class),
                        each.get("paused", Long.class), each.get("abandoned", Long.class), each.get("wontRead", Long.class),
                        each.get("unread", Long.class), each.get("rating", Double.class))).toList(),
                figures.get("total", Long.class), figures.get("averageCompletion", Double.class), number(figures.get("measured")),
                number(figures.get("completed")), number(figures.get("inProgress")), number(figures.get("notStarted")));
    }

    SeriesPage page(String search, String state, Sort sort, int page) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        Columns series = columns(query.from(perSeries(query, query)));
        List<Order> ordering = new ArrayList<>(switch (sort) {
            case PROGRESS -> List.of(cb.desc(series.completion, false));
            case RATING -> List.of(cb.desc(series.rating, false));
            case BOOKS -> List.<Order>of();
            case NAME -> List.of(cb.asc(series.name));
        });
        ordering.addAll(List.of(cb.desc(series.books), cb.asc(series.name)));
        query.multiselect(series.name.alias("series"), series.state, series.books, series.read, series.reading, series.intended,
                        series.completion, series.rating, cb.count(cb.literal(1), cb.createWindow()).alias("matching"))
                .where(filters(series, term, state))
                .orderBy(ordering);
        List<Tuple> rows = entityManager.createQuery(query).setFirstResult(page * PAGE_SIZE).setMaxResults(PAGE_SIZE).getResultList();

        long matching = rows.isEmpty() ? (page == 0 ? 0 : matching(term, state)) : rows.getFirst().get("matching", Long.class);
        Map<String, String> next = nextToRead(rows.stream().map(row -> row.get("series", String.class)).toList());
        return new SeriesPage(rows.stream()
                .map(row -> new SeriesRow(row.get("series", String.class), row.get(1, String.class), row.get(2, Long.class),
                        row.get(3, Long.class), row.get(4, Long.class), row.get(5, Long.class), row.get(6, Double.class),
                        row.get(7, Double.class), next.get(row.get("series", String.class))))
                .toList(), matching);
    }

    private long matching(String term, String state) {
        JpaCriteriaQuery<Long> query = cb.createQuery(Long.class);
        Columns series = columns(query.from(perSeries(query, query)));
        return entityManager.createQuery(query.select(cb.count(cb.literal(1))).where(filters(series, term, state))).getSingleResult();
    }

    private JpaSubQuery<Tuple> perSeries(JpaSelectCriteria<?> parent, CriteriaQuery<?> top) {
        JpaSubQuery<Tuple> grouped = parent.subquery(Tuple.class);
        BookSql books = BookSql.in(cb, grouped, top, scope);
        Path<String> name = books.metadata().get("seriesName");
        Expression<ReadStatus> status = books.status();
        Expression<String> group = cb.<String>selectCase()
                .when(books.finished(), "readBooks")
                .when(status.in(READING, RE_READING), "reading")
                .when(cb.equal(status, PARTIALLY_READ), "partiallyRead")
                .when(cb.equal(status, PAUSED), "paused")
                .when(cb.equal(status, ABANDONED), "abandoned")
                .when(cb.equal(status, WONT_READ), "wontRead")
                .otherwise("unread");
        List<Selection<?>> columns = new ArrayList<>(List.of(name.alias("series"), cb.count(cb.literal(1)).alias("books"),
                cb.avg(cb.toDouble(books.rating())).alias("rating")));
        GROUPS.forEach(each -> columns.add(cb.sumAsLong(BookSql.flag(cb, cb.equal(group, each))).alias(each)));
        books.where.add(name.isNotNull());
        return grouped.multiselect(columns).where(books.where.toArray(Predicate[]::new)).groupBy(name);
    }

    private Columns columns(JpaDerivedRoot<Tuple> row) {
        Path<Long> books = row.get("books");
        Path<Long> read = row.get("readBooks");
        Path<Long> reading = row.get("reading");
        Expression<Long> intended = cb.diff(cb.diff(books, row.<Long>get("abandoned")), row.<Long>get("wontRead"));
        Expression<Double> completion = cb.<Double>selectCase()
                .when(cb.gt(intended, 0L), cb.toDouble(cb.quot(cb.toDouble(read), cb.toDouble(intended))))
                .otherwise(cb.nullLiteral(Double.class));
        Expression<String> state = cb.<String>selectCase()
                .when(cb.equal(intended, 0L), "abandoned")
                .when(cb.equal(read, intended), "completed")
                .when(cb.gt(reading, 0L), "in-progress")
                .when(cb.gt(row.<Long>get("paused"), 0L), "paused")
                .when(cb.gt(cb.sum(read, row.<Long>get("partiallyRead")), 0L), "in-progress")
                .otherwise("not-started");
        return new Columns(row, row.get("series"), books, read, reading, intended, completion, state, row.get("rating"));
    }

    private Predicate[] filters(Columns series, String term, String state) {
        List<Predicate> filters = new ArrayList<>();
        if (!term.isEmpty()) {
            filters.add(cb.gt(cb.locate(cb.lower(series.name), term), 0));
        }
        if (state != null) {
            filters.add(cb.equal(series.state, state));
        }
        return filters.toArray(Predicate[]::new);
    }

    private Map<String, String> nextToRead(List<String> series) {
        if (series.isEmpty()) {
            return Map.of();
        }
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> ranked = query.subquery(Tuple.class);
        BookSql books = BookSql.in(cb, ranked, query, scope);
        Path<String> name = books.metadata().get("seriesName");
        Path<Float> number = books.metadata().get("seriesNumber");
        Expression<String> title = books.title();
        JpaWindow inOrder = cb.createWindow().partitionBy(name).orderBy(cb.asc(number, false), cb.asc(title), cb.asc(books.id()));
        books.where.addAll(List.of(name.in(series),
                books.status().in(STILL_TO_READ), books.finishedAt().isNull()));
        ranked.multiselect(name.alias("series"), title.alias("title"), cb.rowNumber(inOrder).alias("position"))
                .where(books.where.toArray(Predicate[]::new));
        JpaDerivedRoot<Tuple> row = query.from(ranked);
        query.multiselect(row.get("series"), row.get("title")).where(cb.equal(row.get("position"), 1L));
        Map<String, String> next = new LinkedHashMap<>();
        entityManager.createQuery(query).getResultList().forEach(each -> next.put(each.get(0, String.class), each.get(1, String.class)));
        return next;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private record Columns(JpaDerivedRoot<Tuple> row, Expression<String> name, Expression<Long> books, Expression<Long> read,
                           Expression<Long> reading, Expression<Long> intended, Expression<Double> completion, Expression<String> state,
                           Expression<Double> rating) {

        List<Selection<?>> selections() {
            List<Selection<?>> selections = new ArrayList<>(List.of(name.alias("series"), books.alias("books"), rating.alias("rating")));
            GROUPS.forEach(each -> selections.add(row.get(each).alias(each)));
            return selections;
        }
    }
}
