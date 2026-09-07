package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class AuthorVisibleBooks {

    public interface BookCondition {
        Predicate apply(Root<BookEntity> book, Join<?, ?> metadata, CriteriaQuery<?> query, CriteriaBuilder cb);
    }

    public Predicate exists(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        return exists(authorId, query, cb, scope, null);
    }

    public Predicate exists(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope, BookCondition extra) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        sq.select(cb.literal(1L));
        List<Predicate> predicates = predicates(authorId, book, metadata, query, cb, scope);
        if (extra != null) {
            predicates.add(extra.apply(book, metadata, query, cb));
        }
        sq.where(predicates.toArray(Predicate[]::new));
        return cb.exists(sq);
    }

    public Subquery<Long> bookCount(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        sq.select(cb.count(book));
        sq.where(predicates(authorId, book, metadata, query, cb, scope).toArray(Predicate[]::new));
        return sq;
    }

    public Subquery<Long> seriesCount(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        sq.select(cb.countDistinct(metadata.get("seriesName")));
        List<Predicate> predicates = predicates(authorId, book, metadata, query, cb, scope);
        predicates.add(cb.isNotNull(metadata.get("seriesName")));
        sq.where(predicates.toArray(Predicate[]::new));
        return sq;
    }

    public Subquery<Instant> latestAddedOn(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Instant> sq = query.subquery(Instant.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        sq.select(cb.greatest(book.<Instant>get("addedOn")));
        sq.where(predicates(authorId, book, metadata, query, cb, scope).toArray(Predicate[]::new));
        return sq;
    }

    public Subquery<Instant> lastReadTime(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Instant> sq = query.subquery(Instant.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        Join<?, ?> progress = book.join("userBookProgress");
        sq.select(cb.greatest(progress.<Instant>get("lastReadTime")));
        List<Predicate> predicates = predicates(authorId, book, metadata, query, cb, scope);
        predicates.add(userMatches(progress, cb, scope));
        sq.where(predicates.toArray(Predicate[]::new));
        return sq;
    }

    public Subquery<Double> avgMetadataRating(String field, Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Double> sq = query.subquery(Double.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        sq.select(cb.avg(metadata.get(field)));
        sq.where(predicates(authorId, book, metadata, query, cb, scope).toArray(Predicate[]::new));
        return sq;
    }

    public Subquery<Double> avgPersonalRating(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Double> sq = query.subquery(Double.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        Join<?, ?> metadata = book.join("metadata");
        Join<?, ?> progress = book.join("userBookProgress");
        sq.select(cb.avg(progress.get("personalRating")));
        List<Predicate> predicates = predicates(authorId, book, metadata, query, cb, scope);
        predicates.add(userMatches(progress, cb, scope));
        sq.where(predicates.toArray(Predicate[]::new));
        return sq;
    }

    public Predicate bookIdVisible(Expression<Long> bookId, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<BookEntity> book = sq.from(BookEntity.class);
        sq.select(cb.literal(1L));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(book.get("id"), bookId));
        predicates.add(scope.visibleBook(book, query, cb));
        sq.where(predicates.toArray(Predicate[]::new));
        return cb.exists(sq);
    }

    private Predicate hasProgressStatus(Root<BookEntity> book, CriteriaQuery<?> query, CriteriaBuilder cb,
                                        BrowseScope scope, Collection<ReadStatus> statuses) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<UserBookProgressEntity> progress = sq.from(UserBookProgressEntity.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(progress.get("book").get("id"), book.get("id")),
                userMatches(progress, cb, scope),
                progress.get("readStatus").in(statuses));
        return cb.exists(sq);
    }

    public Predicate hasBookWithReadStatus(Path<?> authorId, CriteriaQuery<?> query, CriteriaBuilder cb,
                                           BrowseScope scope, ReadStatus status) {
        BookCondition condition = status == ReadStatus.UNSET
                ? (book, metadata, q, criteria) -> criteria.or(
                        criteria.not(hasAnyProgress(book, q, criteria, scope)),
                        hasProgressStatus(book, q, criteria, scope, List.of(ReadStatus.UNSET)))
                : (book, metadata, q, criteria) -> hasProgressStatus(book, q, criteria, scope, List.of(status));
        return exists(authorId, query, cb, scope, condition);
    }

    private Predicate hasAnyProgress(Root<BookEntity> book, CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<UserBookProgressEntity> progress = sq.from(UserBookProgressEntity.class);
        sq.select(cb.literal(1L));
        sq.where(
                cb.equal(progress.get("book").get("id"), book.get("id")),
                userMatches(progress, cb, scope));
        return cb.exists(sq);
    }

    private List<Predicate> predicates(Path<?> authorId, Root<BookEntity> book, Join<?, ?> metadata,
                                       CriteriaQuery<?> query, CriteriaBuilder cb, BrowseScope scope) {
        Join<?, ?> author = metadata.join("authors");
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(author.get("id"), authorId));
        predicates.add(scope.visibleBook(book, query, cb));
        return predicates;
    }

    private Predicate userMatches(Path<?> progress, CriteriaBuilder cb, BrowseScope scope) {
        return scope.userId() != null
                ? cb.equal(progress.get("user").get("id"), scope.userId())
                : cb.disjunction();
    }
}
