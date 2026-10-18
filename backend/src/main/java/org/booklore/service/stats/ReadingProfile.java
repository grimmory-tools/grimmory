package org.booklore.service.stats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Selection;
import org.booklore.model.dto.stats.StatsResponses.ProfileRow;
import org.booklore.model.dto.stats.StatsResponses.Rows;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.enums.ReadStatus;
import org.hibernate.query.common.TemporalUnit;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaDerivedRoot;
import org.hibernate.query.criteria.JpaSubQuery;
import org.hibernate.query.criteria.JpaWindow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

final class ReadingProfile {

    private static final Map<String, List<String>> GENRE_LISTS = Map.of(
            "intellectual", List.of("philosophy", "history", "biography", "politics", "psychology", "economics", "mathematics",
                    "engineering", "medicine", "law", "education", "sociology", "nonfiction", "non-fiction", "academic"),
            "emotional", List.of("romance", "memoir", "poetry", "drama", "self-help", "autobiography", "literary fiction", "coming of age"),
            "mainstream", List.of("thriller", "mystery", "crime", "suspense", "horror", "fantasy", "science fiction", "adventure",
                    "true crime", "humor", "graphic novel", "manga", "comic"),
            "classic", List.of("classic", "mythology", "folklore", "fairy tale", "ancient", "medieval", "victorian", "gothic"));

    private static final int CLASSIC_AGE_YEARS = 30;
    private static final int POPULAR_GOODREADS_REVIEWS = 10_000;
    private static final int POPULAR_AMAZON_REVIEWS = 2_000;
    private static final int HIGH_PERSONAL_RATING = 8;
    private static final int RECENT_MONTHS = 6;
    private static final int DEEP_DIVE_MINIMUM_BOOKS = 3;
    private static final int FOCUSED_GENRE_MINIMUM_BOOKS = 5;
    private static final double SECONDS_PER_DAY = 86_400;

    private final EntityManager entityManager;
    private final HibernateCriteriaBuilder cb;
    private final Scope scope;

    ReadingProfile(EntityManager entityManager, Scope scope) {
        this.entityManager = entityManager;
        this.cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();
        this.scope = scope;
    }

    Rows<ProfileRow> profile() {
        Map<String, Double> facts = facts();
        double total = facts.get("books");
        if (total == 0) {
            return new Rows<>(List.of());
        }
        facts.putAll(genreShares());
        Function<String, Double> share = fact -> facts.get(fact) / total;
        double languages = languages();
        Names genres = names("categories", FOCUSED_GENRE_MINIMUM_BOOKS);
        Names authors = names("authors", DEEP_DIVE_MINIMUM_BOOKS);
        double read = facts.get("read");
        double rated = facts.get("rated");
        double over600 = facts.get("over600");
        double started = facts.get("started");
        double inSeries = facts.get("inSeries");
        double active = facts.get("reading");

        Map<String, Double> dna = new LinkedHashMap<>();
        dna.put("adventurous", Math.min(75, genres.distinct() / Math.max(1, total * 0.4) * 75)
                + Math.min(25, Math.max(0, languages - 1) * 12.5));
        dna.put("perfectionist", share.apply("read") * 60 + (rated == 0 ? 0 : facts.get("highRated") / rated) * 40);
        dna.put("intellectual", share.apply("intellectual") * 70 + share.apply("over400") * 30);
        dna.put("emotional", share.apply("emotional") * 70 + share.apply("rated") * 30);
        dna.put("patient", share.apply("over500") * 40 + share.apply("seriesNumbered") * 35 + share.apply("progress50") * 25);
        dna.put("social", share.apply("mainstream") * 50 + share.apply("popular") * 50);
        dna.put("nostalgic", share.apply("old") * 60 + share.apply("classic") * 40);
        dna.put("ambitious", Math.min(40, total * 0.4) + share.apply("over600") * 35
                + (over600 == 0 ? 0 : facts.get("readOver600") / over600) * 25);

        double yearSpan = facts.get("latestYear") - facts.get("earliestYear");
        Map<String, Double> habits = new LinkedHashMap<>();
        habits.put("consistency", consistency());
        habits.put("multitasking", Math.min(75, active <= 1 ? active * 10 : 10 + (active - 1) * 20)
                + Math.min(25, facts.get("partial") * 5));
        habits.put("completionism", started == 0 ? 0 : read / started * 75 + (1 - facts.get("abandoned") / started) * 25);
        habits.put("exploration", Math.min(60, authors.distinct() / total * 60) + Math.min(25, yearSpan * 0.5)
                + Math.min(15, Math.max(0, languages - 1) * 7.5));
        habits.put("organization", (read == 0 ? 0 : facts.get("readRated") / read) * 40 + share.apply("statusSet") * 35
                + (inSeries == 0 ? 1 : facts.get("seriesNumbered") / inSeries) * 25);
        habits.put("intensity", facts.get("withPages") == 0 ? 0
                : Math.min(60, facts.get("averagePages") / 10) + Math.min(40, share.apply("progress75") * 40));
        habits.put("methodology", seriesOrderScore()
                + Math.min(30, authors.frequent() * 10)
                + Math.min(20, genres.frequent() * 5));
        habits.put("momentum", Math.min(45, facts.get("recent") * 7.5) + Math.min(30, active * 10)
                + Math.min(25, facts.get("almostDone") * 8));
        return new Rows<>(Stream.concat(rows("dna", dna), rows("habits", habits)).toList());
    }

    private Map<String, Double> facts() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> perBook = query.subquery(Tuple.class);
        BookSql book = new BookSql(cb, perBook, query, scope);
        Expression<ReadStatus> status = book.status();
        Expression<Integer> rating = book.rating();
        Path<String> series = book.metadata().get("seriesName");
        Path<Float> number = book.metadata().get("seriesNumber");
        Instant recentFrom = scope.now().atZone(scope.zone()).minusMonths(RECENT_MONTHS).toInstant();
        int currentYear = scope.now().atZone(scope.zone()).getYear();
        perBook.multiselect(
                        BookSql.flag(cb,book.finished()).alias("readBook"),
                        BookSql.flag(cb,rating.isNotNull()).alias("rated"),
                        BookSql.flag(cb,cb.greaterThanOrEqualTo(rating, HIGH_PERSONAL_RATING)).alias("highRated"),
                        book.pages().alias("pageCount"),
                        book.progressPercent().alias("progress"),
                        BookSql.flag(cb,status.in(ReadStatus.READING, ReadStatus.RE_READING)).alias("reading"),
                        BookSql.flag(cb,cb.equal(status, ReadStatus.ABANDONED)).alias("abandoned"),
                        BookSql.flag(cb,cb.notEqual(status, ReadStatus.UNSET)).alias("statusSet"),
                        BookSql.flag(cb,cb.greaterThan(book.finishedAt(), recentFrom)).alias("recent"),
                        BookSql.flag(cb,series.isNotNull()).alias("inSeries"),
                        BookSql.flag(cb,cb.and(series.isNotNull(), cb.notEqual(number, 0f))).alias("seriesNumbered"),
                        BookSql.flag(cb,cb.or(cb.greaterThan(book.metadata().get("goodreadsReviewCount"), POPULAR_GOODREADS_REVIEWS),
                                cb.greaterThan(book.metadata().get("amazonReviewCount"), POPULAR_AMAZON_REVIEWS))).alias("popular"),
                        book.publicationYear().alias("publishedYear"))
                .where(book.where.toArray(Predicate[]::new));

        JpaDerivedRoot<Tuple> facts = query.from(perBook);
        Expression<Integer> read = facts.get("readBook");
        Expression<Integer> pages = facts.get("pageCount");
        Expression<Double> progress = facts.get("progress");
        Expression<Integer> reading = facts.get("reading");
        Expression<Integer> year = facts.get("publishedYear");
        Map<String, Expression<? extends Number>> totals = new LinkedHashMap<>();
        totals.put("books", cb.count(cb.literal(1)));
        totals.put("read", cb.sum(read));
        Stream.of("rated", "reading", "abandoned", "recent", "highRated", "statusSet", "inSeries", "seriesNumbered", "popular")
                .forEach(fact -> totals.put(fact, cb.sum(facts.<Integer>get(fact))));
        totals.put("over400", count(cb.greaterThan(pages, 400)));
        totals.put("over500", count(cb.greaterThan(pages, 500)));
        totals.put("over600", count(cb.greaterThan(pages, 600)));
        totals.put("readOver600", count(cb.and(cb.equal(read, 1), cb.greaterThan(pages, 600))));
        totals.put("progress50", count(cb.greaterThan(progress, 50.0)));
        totals.put("progress75", count(cb.greaterThan(progress, 75.0)));
        totals.put("almostDone", count(cb.and(cb.greaterThan(progress, 70.0), cb.lessThan(progress, 100.0))));
        totals.put("partial", count(cb.and(cb.equal(reading, 0), cb.greaterThan(progress, 10.0), cb.lessThan(progress, 90.0))));
        totals.put("started", count(cb.or(cb.equal(read, 1), cb.equal(facts.get("abandoned"), 1), cb.equal(reading, 1), cb.greaterThan(progress, 0.0))));
        totals.put("readRated", count(cb.and(cb.equal(read, 1), cb.equal(facts.get("rated"), 1))));
        totals.put("old", count(cb.lessThan(year, currentYear - CLASSIC_AGE_YEARS)));
        totals.put("withPages", cb.count(pages));
        totals.put("averagePages", cb.avg(cb.toDouble(pages)));
        totals.put("earliestYear", cb.min(year));
        totals.put("latestYear", cb.max(year));

        query.multiselect(totals.entrySet().stream().<Selection<?>>map(total -> total.getValue().alias(total.getKey())).toList());
        Tuple row = entityManager.createQuery(query).getSingleResult();
        Map<String, Double> values = new LinkedHashMap<>();
        for (String name : totals.keySet()) {
            Number value = (Number) row.get(name);
            values.put(name, value == null ? 0 : value.doubleValue());
        }
        return values;
    }

    private double consistency() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> finishes = query.subquery(Tuple.class);
        BookSql book = new BookSql(cb, finishes, query, scope);
        Expression<Instant> finished = book.finishedAt();
        JpaWindow inOrder = cb.createWindow().orderBy(cb.asc(finished));
        book.where.add(finished.isNotNull());
        finishes.multiselect(finished.alias("finished"), cb.windowFunction("lag", Instant.class, inOrder, finished).alias("previous"))
                .where(book.where.toArray(Predicate[]::new));

        JpaDerivedRoot<Tuple> row = query.from(finishes);
        Expression<Double> gap = cb.toDouble(cb.durationByUnit(TemporalUnit.SECOND,
                cb.durationBetween(row.<Instant>get("finished"), row.<Instant>get("previous"))));
        query.multiselect(cb.count(cb.literal(1)), cb.avg(gap), cb.avg(cb.prod(gap, gap)));
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        long count = ((Number) totals.get(0)).longValue();
        if (count < 3) {
            return Math.min(20, count * 10);
        }
        double meanGap = ((Number) totals.get(1)).doubleValue() / SECONDS_PER_DAY;
        if (meanGap == 0) {
            return 50;
        }
        double meanSquare = ((Number) totals.get(2)).doubleValue() / (SECONDS_PER_DAY * SECONDS_PER_DAY);
        double variation = Math.sqrt(Math.max(0, meanSquare - meanGap * meanGap)) / meanGap;
        return Math.clamp((1 - variation / 2) * 70, 0, 70) + Math.min(30, count * 1.5);
    }

    private double seriesOrderScore() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> finishes = query.subquery(Tuple.class);
        BookSql book = new BookSql(cb, finishes, query, scope);
        Expression<Instant> finished = book.finishedAt();
        Path<Float> number = book.metadata().get("seriesNumber");
        Path<String> series = book.metadata().get("seriesName");
        JpaWindow bySeries = cb.createWindow().partitionBy(series).orderBy(cb.asc(number), cb.asc(book.id()));
        book.where.addAll(List.of(series.isNotNull(), cb.notEqual(number, 0f), finished.isNotNull()));
        finishes.multiselect(
                        series.alias("series"),
                        finished.alias("finished"),
                        cb.windowFunction("lag", Instant.class, bySeries, finished).alias("previous"),
                        cb.count(cb.literal(1), cb.createWindow().partitionBy(series)).alias("finishedInSeries"))
                .where(book.where.toArray(Predicate[]::new));

        JpaDerivedRoot<Tuple> row = query.from(finishes);
        Expression<String> name = row.get("series");
        query.multiselect(
                cb.countDistinct(cb.<String>selectCase()
                        .when(cb.greaterThanOrEqualTo(row.<Long>get("finishedInSeries"), 2L), name)
                        .otherwise(cb.nullLiteral(String.class))),
                cb.countDistinct(cb.<String>selectCase()
                        .when(cb.lessThan(row.<Instant>get("finished"), row.<Instant>get("previous")), name)
                        .otherwise(cb.nullLiteral(String.class))));
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        long multiBookSeries = ((Number) totals.get(0)).longValue();
        long outOfOrder = ((Number) totals.get(1)).longValue();
        return multiBookSeries > 0 ? (double) (multiBookSeries - outOfOrder) / multiBookSeries * 50 : 25;
    }

    private Names names(String collection, int minimum) {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> perName = query.subquery(Tuple.class);
        BookSql book = new BookSql(cb, perName, query, scope);
        Path<Long> name = book.metadata().join(collection).get("id");
        perName.multiselect(name.alias("name"), cb.countDistinct(book.id()).alias("books"))
                .where(book.where.toArray(Predicate[]::new))
                .groupBy(name);
        JpaDerivedRoot<Tuple> row = query.from(perName);
        query.multiselect(cb.count(cb.literal(1)), count(cb.ge(row.<Long>get("books"), minimum)));
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        Number frequent = (Number) totals.get(1);
        return new Names(((Number) totals.get(0)).longValue(), frequent == null ? 0 : frequent.longValue());
    }

    private record Names(long distinct, long frequent) {
    }

    private double languages() {
        return new BookQueries(entityManager, scope).languageCounts().keySet().stream().filter(Objects::nonNull).count();
    }

    private Map<String, Double> genreShares() {
        JpaCriteriaQuery<Tuple> query = cb.createTupleQuery();
        JpaSubQuery<Tuple> perBook = query.subquery(Tuple.class);
        JpaSubQuery<Tuple> classified = perBook.subquery(Tuple.class);
        Root<CategoryEntity> category = classified.from(CategoryEntity.class);
        Path<Long> id = category.get("id");
        Expression<String> name = cb.lower(cb.trim(category.get("name")));
        List<Selection<?>> kinds = new ArrayList<>(List.of(id.alias("genre")));
        List<Predicate> any = new ArrayList<>();
        GENRE_LISTS.forEach((kind, keywords) -> {
            Predicate matches = cb.or(keywords.stream().map(keyword -> cb.like(name, "%" + keyword + "%")).toArray(Predicate[]::new));
            kinds.add(cb.max(BookSql.flag(cb, matches)).alias(kind));
            any.add(matches);
        });
        classified.multiselect(kinds).where(cb.or(any.toArray(Predicate[]::new))).groupBy(id);

        BookSql book = new BookSql(cb, perBook, query, scope);
        Path<Long> genre = book.metadata().join("categories").get("id");
        JpaDerivedRoot<Tuple> kind = perBook.from(classified);
        List<Selection<?>> perBookColumns = new ArrayList<>(List.of(book.id().alias("book")));
        GENRE_LISTS.keySet().forEach(each -> perBookColumns.add(cb.max(kind.<Integer>get(each)).alias(each)));
        book.where.add(cb.equal(genre, kind.get("genre")));
        perBook.multiselect(perBookColumns).where(book.where.toArray(Predicate[]::new)).groupBy(book.id());

        JpaDerivedRoot<Tuple> marked = query.from(perBook);
        query.multiselect(GENRE_LISTS.keySet().stream().<Selection<?>>map(each -> cb.sum(marked.<Integer>get(each)).alias(each)).toList());
        Tuple row = entityManager.createQuery(query).getSingleResult();
        Map<String, Double> shares = new LinkedHashMap<>();
        for (String each : GENRE_LISTS.keySet()) {
            Number books = (Number) row.get(each);
            shares.put(each, books == null ? 0 : books.doubleValue());
        }
        return shares;
    }

    private Expression<Long> count(Predicate predicate) {
        return cb.sumAsLong(BookSql.flag(cb, predicate));
    }

    private static Stream<ProfileRow> rows(String profile, Map<String, Double> scores) {
        return scores.entrySet().stream()
                .map(entry -> new ProfileRow(profile, entry.getKey(), (int) Math.min(100, Math.round(entry.getValue()))));
    }
}
