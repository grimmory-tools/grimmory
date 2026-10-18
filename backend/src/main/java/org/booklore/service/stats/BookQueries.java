package org.booklore.service.stats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import org.booklore.model.dto.stats.StatsResponses.ActivityMonth;
import org.booklore.model.dto.stats.StatsResponses.BookFlow;
import org.booklore.model.dto.stats.StatsResponses.ComparisonCell;
import org.booklore.model.dto.stats.StatsResponses.Count;
import org.booklore.model.dto.stats.StatsResponses.DecadeCell;
import org.booklore.model.dto.stats.StatsResponses.Distribution;
import org.booklore.model.dto.stats.StatsResponses.LengthCell;
import org.booklore.model.dto.stats.StatsResponses.RatingFlow;
import org.booklore.model.dto.stats.StatsResponses.Rows;
import org.booklore.model.dto.stats.StatsResponses.StatusCount;
import org.booklore.model.dto.stats.StatsResponses.TopName;
import org.booklore.model.dto.stats.StatsResponses.YearFlow;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.util.LanguageNormalizer;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaDerivedRoot;
import org.hibernate.query.criteria.JpaSearchedCase;
import org.hibernate.query.criteria.JpaSubQuery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

final class BookQueries {

    enum Dimension {
        FORMAT(false, BookSql::format, books -> books.cb.or(books.cb.isNotNull(books.format()), books.cb.isFalse(books.physical()))),
        LANGUAGE(false, BookSql::language),
        METADATA_SCORE(false, BookQueries::metadataBand),
        PAGE_BAND(false, books -> banded(books.cb, books.pages(), PAGE_BANDS)),
        PUBLICATION_YEAR(true, BookSql::publicationYear),
        STATUS(false, BookSql::status),
        RATING(true, BookSql::rating),
        STOPPED(true, BookSql::progressBucket, books -> books.cb.not(books.status().in(ReadStatus.READING, ReadStatus.RE_READING)));

        private final boolean inValueOrder;
        private final Function<BookSql, Expression<?>> value;
        private final Function<BookSql, Predicate> counted;

        Dimension(boolean inValueOrder, Function<BookSql, Expression<?>> value) {
            this(inValueOrder, value, null);
        }

        Dimension(boolean inValueOrder, Function<BookSql, Expression<?>> value, Function<BookSql, Predicate> counted) {
            this.inValueOrder = inValueOrder;
            this.value = value;
            this.counted = counted;
        }
    }

    enum Kind {
        AUTHOR("authors"),
        GENRE("categories"),
        TAG("tags"),
        MOOD("moods"),
        SERIES("seriesName"),
        PUBLISHER("publisher");

        private final String attribute;

        Kind(String attribute) {
            this.attribute = attribute;
        }
    }

    enum Measure {
        BOOKS, READ, RATING
    }

    private static final int LANGUAGES_SHOWN = 6;
    private static final int TOP_NAMES = 10;
    private static final List<ReadStatus> STATUSES = List.of(ReadStatus.values());
    private static final int RATED_AT_LEAST = 2;
    private static final List<Band> PAGE_BANDS = List.of(
            new Band("up-to-100", 1, 100),
            new Band("101-to-200", 101, 200),
            new Band("201-to-300", 201, 300),
            new Band("301-to-500", 301, 500),
            new Band("501-to-750", 501, 750),
            new Band("751-to-1000", 751, 1000),
            new Band("over-1000", 1001, null));
    private static final List<Band> RATING_LEVELS = List.of(new Band("low", 1, 4), new Band("mid", 5, 6), new Band("high", 7, 10));
    private static final int EARLIEST_DECADE = 1900;
    private static final List<Threshold> METADATA_BANDS = List.of(
            new Threshold("excellent", 90), new Threshold("good", 70), new Threshold("fair", 50), new Threshold("poor", 25), new Threshold("very-poor", 0));

    private record Band(String id, int minimum, Integer maximum) {}

    private record Threshold(String id, int from) {}

    private record Tally(long added, long finished, long pages, long backlogChange) {
        Tally plus(Tally other) {
            return new Tally(added + other.added, finished + other.finished, pages + other.pages, backlogChange + other.backlogChange);
        }
    }

    private final EntityManager entityManager;
    private final HibernateCriteriaBuilder cb;
    private final Scope scope;

    BookQueries(EntityManager entityManager, Scope scope) {
        this.entityManager = entityManager;
        this.cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();
        this.scope = scope;
    }

    Distribution distribution(Dimension dimension) {
        return switch (dimension) {
            case FORMAT -> formats();
            case LANGUAGE -> languages();
            case METADATA_SCORE -> metadataScores();
            default -> new Distribution(counts(dimension), null, null);
        };
    }

    Rows<LengthCell> length() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        BookSql books = new BookSql(cb, query, scope);
        Expression<String> band = banded(cb, books.pages(), PAGE_BANDS);
        Expression<Integer> rating = books.rating();
        books.where.add(cb.gt(books.pages(), 0));
        books.where.add(rating.isNotNull());
        query.multiselect(band, rating, cb.count(books.id())).where(books.where.toArray(Predicate[]::new)).groupBy(band, rating);
        return new Rows<>(entityManager.createQuery(query).getResultList().stream()
                .map(row -> new LengthCell(row.get(0, String.class), row.get(1, Integer.class), row.get(2, Long.class)))
                .toList());
    }

    Rows<ComparisonCell> comparison() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        BookSql books = new BookSql(cb, query, scope);
        Expression<Double> outside = books.outsideRating();
        Expression<Integer> rating = books.rating();
        Expression<Integer> halfStars = cb.toInteger(cb.floor(cb.sum(cb.round(cb.prod(outside, 2.0), 4), 0.5)));
        Expression<Double> gap = cb.diff(cb.toDouble(cb.quot(cb.toDouble(rating), 2.0)), outside);
        books.where.add(outside.isNotNull());
        books.where.add(rating.isNotNull());
        query.multiselect(halfStars, rating, cb.count(books.id()), cb.sum(gap))
                .where(books.where.toArray(Predicate[]::new))
                .groupBy(halfStars, rating);
        return new Rows<>(entityManager.createQuery(query).getResultList().stream()
                .map(row -> new ComparisonCell(row.get(0, Integer.class), row.get(1, Integer.class), row.get(2, Long.class), row.get(3, Double.class)))
                .toList());
    }

    Rows<DecadeCell> decade() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        BookSql books = new BookSql(cb, query, scope);
        Expression<Integer> year = books.publicationYear();
        Expression<Integer> decade = cb.prod(cb.toInteger(cb.floor(cb.quot(cb.toDouble(year), 10.0))), 10);
        Expression<Integer> rating = books.rating();
        books.where.add(cb.ge(year, EARLIEST_DECADE));
        books.where.add(rating.isNotNull());
        query.multiselect(decade, rating, cb.count(books.id())).where(books.where.toArray(Predicate[]::new)).groupBy(decade, rating);
        return new Rows<>(entityManager.createQuery(query).getResultList().stream()
                .map(row -> new DecadeCell(row.get(0, Integer.class), row.get(1, Integer.class), row.get(2, Long.class)))
                .toList());
    }

    BookFlow flow() {
        JpaCriteriaQuery<Tuple> byYear = cb.createTupleQuery();
        BookSql added = new BookSql(cb, byYear, scope);
        Expression<Integer> year = added.yearKey(added.addedOn());
        Expression<String> group = statusGroup(added);
        byYear.multiselect(year, group, cb.count(added.id())).where(added.where.toArray(Predicate[]::new)).groupBy(year, group);

        JpaCriteriaQuery<Tuple> byRating = cb.createTupleQuery();
        BookSql rated = new BookSql(cb, byRating, scope);
        Expression<String> status = statusGroup(rated);
        Expression<String> level = banded(cb, rated.rating(), RATING_LEVELS);
        byRating.multiselect(status, level, cb.count(rated.id())).where(rated.where.toArray(Predicate[]::new)).groupBy(status, level);

        return new BookFlow(
                entityManager.createQuery(byYear).getResultList().stream()
                        .map(row -> new YearFlow(row.get(0, Integer.class), row.get(1, String.class), row.get(2, Long.class)))
                        .toList(),
                entityManager.createQuery(byRating).getResultList().stream()
                        .map(row -> new RatingFlow(row.get(0, String.class), row.get(1, String.class), row.get(2, Long.class)))
                        .toList());
    }

    Rows<ActivityMonth> activity() {
        JpaCriteriaQuery<Tuple> addedQuery = cb.createTupleQuery();
        JpaSubQuery<Tuple> addedBooks = addedQuery.subquery(Tuple.class);
        BookSql added = new BookSql(cb, addedBooks, addedQuery, scope);
        added.where.add(added.addedOn().isNotNull());
        addedBooks.multiselect(added.addedOn().alias("added"), added.finishedAt().alias("finished")).where(added.where.toArray(Predicate[]::new));
        JpaDerivedRoot<Tuple> addedBook = addedQuery.from(addedBooks);
        Expression<Instant> addedOn = addedBook.get("added");
        Expression<Integer> addedMonth = added.monthKey(addedOn);
        Expression<Integer> joins = BookSql.flag(cb, cb.or(addedBook.get("finished").isNull(), cb.lessThanOrEqualTo(addedOn, addedBook.get("finished"))));
        addedQuery.multiselect(addedMonth, cb.count(cb.literal(1)), cb.sumAsLong(joins)).groupBy(addedMonth);

        JpaCriteriaQuery<Tuple> finishedQuery = cb.createTupleQuery();
        JpaSubQuery<Tuple> finishedBooks = finishedQuery.subquery(Tuple.class);
        BookSql read = new BookSql(cb, finishedBooks, finishedQuery, scope);
        read.where.add(read.finishedAt().isNotNull());
        finishedBooks.multiselect(read.finishedAt().alias("finished"), read.addedOn().alias("added"), read.pages().alias("pages"))
                .where(read.where.toArray(Predicate[]::new));
        JpaDerivedRoot<Tuple> book = finishedQuery.from(finishedBooks);
        Expression<Instant> finishedOn = book.get("finished");
        Expression<Integer> finishedMonth = read.monthKey(finishedOn);
        Expression<Integer> leaves = BookSql.flag(cb, cb.lessThanOrEqualTo(book.get("added"), finishedOn));
        finishedQuery.multiselect(finishedMonth, cb.count(cb.literal(1)), cb.sumAsLong(book.get("pages")), cb.sumAsLong(leaves))
                .groupBy(finishedMonth);

        Map<Integer, Tally> months = new TreeMap<>();
        for (Tuple row : entityManager.createQuery(addedQuery).getResultList()) {
            months.merge(row.get(0, Integer.class), new Tally(row.get(1, Long.class), 0, 0, row.get(2, Long.class)), Tally::plus);
        }
        for (Tuple row : entityManager.createQuery(finishedQuery).getResultList()) {
            Long pages = row.get(2, Long.class);
            months.merge(row.get(0, Integer.class), new Tally(0, row.get(1, Long.class), pages == null ? 0 : pages, -row.get(3, Long.class)),
                    Tally::plus);
        }
        return new Rows<>(months.entrySet().stream()
                .map(month -> new ActivityMonth("%04d-%02d".formatted(month.getKey() / 100, month.getKey() % 100),
                        month.getValue().added(), month.getValue().finished(), month.getValue().pages(), month.getValue().backlogChange()))
                .toList());
    }

    Rows<TopName> top(Kind kind, Measure measure) {
        JpaCriteriaQuery<Tuple> ranking = cb.createTupleQuery();
        NamedBooks books = kind == Kind.AUTHOR ? authorBooks(ranking) : namedBooks(ranking, kind);
        Expression<String> name = books.name();
        Expression<Long> count = cb.count(cb.literal(1));
        Expression<Long> read = cb.sumAsLong(books.read());
        Expression<Long> rated = cb.count(books.rating());
        Expression<Double> rating = cb.avg(cb.toDouble(books.rating()));
        List<Selection<?>> columns = new ArrayList<>(List.of(name, count, read, rating));
        STATUSES.forEach(status -> columns.add(cb.sumAsLong(books.statuses().get(status)).alias("status" + status.ordinal())));
        ranking.multiselect(columns).where(books.where().toArray(Predicate[]::new)).groupBy(name);
        switch (measure) {
            case BOOKS -> ranking.orderBy(cb.desc(count), cb.asc(name));
            case READ -> ranking.having(cb.gt(read, 0L)).orderBy(cb.desc(read), cb.desc(count), cb.asc(name));
            case RATING -> ranking.having(cb.ge(rated, (long) RATED_AT_LEAST)).orderBy(cb.desc(rating), cb.desc(rated), cb.asc(name));
        }
        return new Rows<>(entityManager.createQuery(ranking).setMaxResults(TOP_NAMES).getResultList().stream()
                .map(row -> new TopName(row.get(0, String.class), row.get(1, Long.class), statuses(row), row.get(3, Double.class)))
                .toList());
    }

    private record NamedBooks(Expression<String> name, Expression<Integer> read, Expression<Integer> rating,
                              Map<ReadStatus, Expression<Integer>> statuses, List<Predicate> where) {}

    private NamedBooks namedBooks(JpaCriteriaQuery<Tuple> ranking, Kind kind) {
        BookSql books = new BookSql(cb, ranking, scope);
        Expression<String> name = name(books, kind);
        books.where.add(name.isNotNull());
        Map<ReadStatus, Expression<Integer>> statuses = new EnumMap<>(ReadStatus.class);
        STATUSES.forEach(status -> statuses.put(status, BookSql.flag(cb, cb.equal(books.status(), status))));
        return new NamedBooks(name, BookSql.flag(cb, books.isRead()), books.rating(), statuses, books.where);
    }

    private NamedBooks authorBooks(JpaCriteriaQuery<Tuple> ranking) {
        JpaSubQuery<Tuple> perBook = ranking.subquery(Tuple.class);
        BookSql books = new BookSql(cb, perBook, ranking, scope);
        Path<Long> author = books.metadata().join(Kind.AUTHOR.attribute).get("id");
        List<Selection<?>> columns = new ArrayList<>(List.of(author.alias("author"),
                cb.max(BookSql.flag(cb, books.isRead())).alias("readBook"), cb.max(books.rating()).alias("rating")));
        STATUSES.forEach(status -> columns.add(cb.max(BookSql.flag(cb, cb.equal(books.status(), status))).alias("status" + status.ordinal())));
        perBook.multiselect(columns).where(books.where.toArray(Predicate[]::new)).groupBy(author, books.id());

        JpaDerivedRoot<Tuple> book = ranking.from(perBook);
        Root<AuthorEntity> named = ranking.from(AuthorEntity.class);
        Map<ReadStatus, Expression<Integer>> statuses = new EnumMap<>(ReadStatus.class);
        STATUSES.forEach(status -> statuses.put(status, book.get("status" + status.ordinal())));
        return new NamedBooks(named.get("name"), book.get("readBook"), book.get("rating"), statuses,
                List.of(cb.equal(named.get("id"), book.get("author")), named.get("name").isNotNull()));
    }

    private static List<StatusCount> statuses(Tuple row) {
        return STATUSES.stream()
                .map(status -> new StatusCount(status.name(), row.get("status" + status.ordinal(), Long.class)))
                .filter(status -> status.books() > 0)
                .sorted(Comparator.comparingLong(StatusCount::books).reversed().thenComparing(StatusCount::status))
                .toList();
    }

    private static Expression<String> name(BookSql books, Kind kind) {
        return switch (kind) {
            case SERIES, PUBLISHER -> books.metadata().get(kind.attribute);
            default -> books.metadata().join(kind.attribute).get("name");
        };
    }

    private List<Count> counts(Dimension dimension) {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        BookSql books = new BookSql(cb, query, scope);
        Expression<?> value = dimension.value.apply(books);
        Expression<Long> count = cb.countDistinct(books.id());
        if (dimension.counted != null) {
            books.where.add(dimension.counted.apply(books));
        }
        query.multiselect(value, count)
                .where(books.where.toArray(Predicate[]::new))
                .groupBy(value)
                .orderBy(dimension.inValueOrder ? List.of(cb.asc(value)) : List.of(cb.desc(count), cb.asc(value)));
        return entityManager.createQuery(query).getResultList().stream()
                .map(row -> new Count(row.get(0), row.get(1, Long.class)))
                .toList();
    }

    Map<String, Long> languageCounts() {
        Map<String, Long> languages = new LinkedHashMap<>();
        for (Count row : counts(Dimension.LANGUAGE)) {
            String raw = (String) row.value();
            languages.merge(raw == null ? null : LanguageNormalizer.normalize(raw), row.books(), Long::sum);
        }
        return languages;
    }

    private Distribution languages() {
        Map<String, Long> languages = languageCounts();
        List<Map.Entry<String, Long>> named = languages.entrySet().stream()
                .filter(language -> language.getKey() != null)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .toList();
        List<Count> rows = new ArrayList<>();
        named.stream().limit(LANGUAGES_SHOWN).forEach(language -> rows.add(new Count(language.getKey(), language.getValue())));
        if (languages.containsKey(null)) {
            rows.add(new Count(null, languages.get(null)));
        }
        long other = named.stream().skip(LANGUAGES_SHOWN).mapToLong(Map.Entry::getValue).sum();
        return new Distribution(rows, other, null);
    }

    private Distribution formats() {
        JpaCriteriaQuery<Long> query = cb.createQuery(Long.class);
        BookSql books = new BookSql(cb, query, scope);
        books.where.add(cb.isTrue(books.physical()));
        long physical = entityManager.createQuery(query.select(cb.count(books.id())).where(books.where.toArray(Predicate[]::new))).getSingleResult();
        List<Count> rows = new ArrayList<>(counts(Dimension.FORMAT));
        if (physical > 0) {
            rows.add(new Count("PHYSICAL", physical));
        }
        rows.sort(Comparator.comparingLong(Count::books).reversed());
        return new Distribution(rows, null, null);
    }

    private Distribution metadataScores() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        BookSql books = new BookSql(cb, query, scope);
        Expression<String> band = metadataBand(books);
        Expression<Long> count = cb.count(books.id());
        query.multiselect(band, count, cb.sum(cb.toDouble(validScore(books))))
                .where(books.where.toArray(Predicate[]::new))
                .groupBy(band)
                .orderBy(cb.desc(count), cb.asc(band));
        List<Tuple> bands = entityManager.createQuery(query).getResultList();
        long scored = bands.stream().filter(row -> row.get(0) != null).mapToLong(row -> row.get(1, Long.class)).sum();
        double total = bands.stream().filter(row -> row.get(0) != null).mapToDouble(row -> row.get(2, Double.class)).sum();
        List<Count> rows = bands.stream().map(row -> new Count(row.get(0), row.get(1, Long.class))).toList();
        return new Distribution(rows, null, scored == 0 ? null : total / scored);
    }

    private static Expression<String> statusGroup(BookSql books) {
        HibernateCriteriaBuilder cb = books.cb;
        Expression<ReadStatus> status = books.status();
        return cb.<String>selectCase()
                .when(cb.equal(status, ReadStatus.READ), "read")
                .when(status.in(ReadStatus.READING, ReadStatus.RE_READING, ReadStatus.PARTIALLY_READ), "reading")
                .when(cb.equal(status, ReadStatus.PAUSED), "paused")
                .when(status.in(ReadStatus.ABANDONED, ReadStatus.WONT_READ), "abandoned")
                .otherwise("unread");
    }

    private static Expression<String> metadataBand(BookSql books) {
        Expression<Float> score = validScore(books);
        JpaSearchedCase<String> band = books.cb.selectCase();
        for (Threshold each : METADATA_BANDS) {
            band.when(books.cb.ge(score, each.from()), each.id());
        }
        return band.otherwise(books.cb.nullLiteral(String.class));
    }

    private static Expression<Float> validScore(BookSql books) {
        Path<Float> score = books.book.get("metadataMatchScore");
        return books.cb.<Float>selectCase().when(books.cb.between(score, 0f, 100f), score).otherwise(books.cb.nullLiteral(Float.class));
    }

    private static Expression<String> banded(HibernateCriteriaBuilder cb, Expression<Integer> value, List<Band> bands) {
        JpaSearchedCase<String> band = cb.selectCase();
        for (Band each : bands) {
            band.when(each.maximum() == null
                    ? cb.greaterThanOrEqualTo(value, each.minimum())
                    : cb.between(value, each.minimum(), each.maximum()), each.id());
        }
        return band.otherwise(cb.nullLiteral(String.class));
    }
}
