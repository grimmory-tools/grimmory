package org.booklore.service.stats;

import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaDerivedJoin;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.criteria.JpaSearchedCase;
import org.hibernate.query.criteria.JpaSelectCriteria;
import org.hibernate.query.criteria.JpaSubQuery;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.List;

final class BookSql {

    static final Instant EVENTS_FROM = Instant.EPOCH;

    private static final List<String> OUTSIDE_RATINGS = List.of(
            "goodreadsRating", "amazonRating", "hardcoverRating", "audibleRating", "applebooksRating", "lubimyczytacRating", "ranobedbRating");

    final HibernateCriteriaBuilder cb;
    private final Scope scope;
    final JpaRoot<BookEntity> book;
    final List<Predicate> where = new ArrayList<>();
    private final JpaSelectCriteria<?> query;
    private Join<BookEntity, BookMetadataEntity> metadata;
    private Join<BookEntity, UserBookProgressEntity> progress;
    private JpaDerivedJoin<Tuple> fileProgress;
    private Join<BookEntity, BookFileEntity> files;

    BookSql(HibernateCriteriaBuilder cb, JpaCriteriaQuery<?> query, Scope scope) {
        this(cb, query, query, scope);
    }

    BookSql(HibernateCriteriaBuilder cb, JpaSelectCriteria<?> query, CriteriaQuery<?> top, Scope scope) {
        this.cb = cb;
        this.query = query;
        this.scope = scope;
        this.book = query.from(BookEntity.class);
        Predicate filter = scope.matching().toPredicate(book, top, cb);
        if (filter != null) {
            where.add(filter);
        }
    }

    static Expression<Integer> flag(HibernateCriteriaBuilder cb, Predicate predicate) {
        return cb.<Integer>selectCase().when(predicate, 1).otherwise(0);
    }

    Path<Long> id() {
        return book.get("id");
    }

    Join<BookEntity, BookMetadataEntity> metadata() {
        if (metadata == null) {
            metadata = book.join("metadata", JoinType.LEFT);
        }
        return metadata;
    }

    Join<BookEntity, UserBookProgressEntity> progress() {
        if (progress == null) {
            progress = book.join("userBookProgress", JoinType.LEFT);
            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
        }
        return progress;
    }

    Expression<ReadStatus> status() {
        return cb.coalesce(progress().<ReadStatus>get("readStatus"), ReadStatus.UNSET);
    }

    Predicate isRead() {
        return cb.equal(progress().get("readStatus"), ReadStatus.READ);
    }

    Predicate finished() {
        return cb.or(isRead(), finishedAt().isNotNull());
    }

    Expression<Integer> rating() {
        return progress().get("personalRating");
    }

    Expression<Double> progressPercent() {
        Join<BookEntity, UserBookProgressEntity> progress = progress();
        Expression<Double> furthest = cb.function("greatest", Double.class,
                cb.literal(0.0),
                zeroIfNull(furthestInAFile()),
                zeroIfNull(progress.get("pdfProgressPercent")),
                zeroIfNull(progress.get("epubProgressPercent")),
                zeroIfNull(progress.get("cbxProgressPercent")),
                zeroIfNull(progress.get("koboProgressPercent")),
                cb.prod(zeroIfNull(progress.get("koreaderProgressPercent")), 100.0));
        return cb.<Double>selectCase()
                .when(isRead(), 100.0)
                .otherwise(cb.function("least", Double.class, furthest, cb.literal(100.0)));
    }

    Expression<Integer> progressBucket() {
        Expression<Double> progress = progressPercent();
        return cb.<Integer>selectCase()
                .when(cb.greaterThanOrEqualTo(progress, 100.0), 100)
                .when(cb.lessThanOrEqualTo(progress, 0.0), 0)
                .when(cb.lessThan(progress, 1.0), 1)
                .otherwise(cb.toInteger(cb.floor(progress)));
    }

    Expression<Instant> finishedAt() {
        Expression<Instant> markedRead = cb.<Instant>selectCase()
                .when(isRead(), progress().<Instant>get("readStatusModifiedTime"))
                .otherwise(cb.nullLiteral(Instant.class));
        return cb.coalesce(progress().<Instant>get("dateFinished"), markedRead);
    }

    Expression<Instant> addedOn() {
        return book.get("addedOn");
    }

    Expression<Integer> pages() {
        return metadata().get("pageCount");
    }

    Expression<String> format() {
        if (files == null) {
            files = book.join("bookFiles", JoinType.LEFT);
            files.on(cb.isTrue(files.get("isBookFormat")));
        }
        return files.get("bookType").cast(String.class);
    }

    Expression<Boolean> physical() {
        return book.get("isPhysical");
    }

    Expression<String> title() {
        return metadata().get("title");
    }

    Expression<Double> outsideRating() {
        Expression<Double> total = cb.literal(0.0);
        Expression<Integer> providers = cb.literal(0);
        for (String provider : OUTSIDE_RATINGS) {
            Path<Double> rating = metadata().get(provider);
            total = cb.sum(total, cb.coalesce(rating, 0.0));
            providers = cb.sum(providers, flag(cb, rating.isNotNull()));
        }
        return cb.<Double>selectCase()
                .when(cb.greaterThan(providers, 0), cb.toDouble(cb.quot(total, cb.toDouble(providers))))
                .otherwise(cb.nullLiteral(Double.class));
    }

    Expression<String> language() {
        return metadata().get("language");
    }

    Expression<Integer> publicationYear() {
        return cb.year(metadata().<LocalDate>get("publishedDate"));
    }

    Expression<Integer> monthKey(Expression<Instant> instant) {
        Expression<Instant> local = local(instant, EVENTS_FROM);
        return cb.sum(cb.prod(cb.year(local), 100), cb.month(local));
    }

    Expression<Integer> yearKey(Expression<Instant> instant) {
        return cb.year(local(instant, EVENTS_FROM));
    }

    Expression<Instant> local(Expression<Instant> instant, Instant from) {
        ZoneRules rules = scope.zone().getRules();
        ZoneOffset first = rules.getOffset(from);
        List<ZoneOffsetTransition> changes = new ArrayList<>();
        for (ZoneOffsetTransition change = rules.nextTransition(from);
             change != null && change.getInstant().isBefore(scope.now());
             change = rules.nextTransition(change.getInstant())) {
            changes.add(change);
        }
        if (changes.isEmpty()) {
            return shift(instant, cb.literal(first.getTotalSeconds()));
        }
        JpaSearchedCase<Integer> offset = cb.selectCase();
        for (ZoneOffsetTransition change : changes.reversed()) {
            offset.when(cb.greaterThanOrEqualTo(instant, cb.literal(change.getInstant())),
                    cb.literal(change.getOffsetAfter().getTotalSeconds()));
        }
        return shift(instant, offset.otherwise(cb.literal(first.getTotalSeconds())));
    }

    private Expression<Float> furthestInAFile() {
        if (fileProgress == null) {
            JpaSubQuery<Tuple> perBook = query.subquery(Tuple.class);
            Root<UserBookFileProgressEntity> file = perBook.from(UserBookFileProgressEntity.class);
            Path<Long> owner = file.get("bookFile").get("book").get("id");
            perBook.multiselect(owner.alias("book"), cb.max(file.<Float>get("progressPercent")).alias("furthest"))
                    .where(cb.equal(file.get("user").get("id"), scope.userId()))
                    .groupBy(owner);
            fileProgress = book.join(perBook, org.hibernate.query.common.JoinType.LEFT);
            fileProgress.on(cb.equal(fileProgress.get("book"), book.get("id")));
        }
        return fileProgress.get("furthest");
    }

    private Expression<Instant> shift(Expression<Instant> instant, Expression<Integer> seconds) {
        return cb.addDuration(instant, cb.durationScaled(seconds, Duration.ofSeconds(1)));
    }

    private Expression<Double> zeroIfNull(Expression<? extends Number> value) {
        return cb.coalesce(value.as(Double.class), 0.0);
    }
}
