package org.booklore.service.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.booklore.browse.SortContext;
import org.booklore.browse.SortOrderBuilder;
import org.booklore.browse.SortRegistry;
import org.booklore.model.entity.BookEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookSortRegistry {

    private static final List<String> PROGRESS_PERCENT_FIELDS = List.of(
            "pdfProgressPercent", "epubProgressPercent", "cbxProgressPercent",
            "koreaderProgressPercent", "koboProgressPercent");

    private final SortRegistry<BookEntity> registry = build();

    public SortRegistry<BookEntity> registry() {
        return registry;
    }

    private static SortRegistry<BookEntity> build() {
        SortRegistry<BookEntity> registry = new SortRegistry<>();

        registry.register("id", rootField("id"));
        registry.register("addedOn", rootField("addedOn"));

        for (String field : List.of(
                "title", "seriesName", "seriesNumber", "publisher", "publishedDate",
                "amazonRating", "amazonReviewCount", "goodreadsRating", "goodreadsReviewCount",
                "hardcoverRating", "hardcoverReviewCount", "ranobedbRating",
                "narrator", "pageCount", "language")) {
            registry.register(field, metadataField(field));
        }

        for (String field : List.of("personalRating", "lastReadTime", "readStatus", "dateFinished")) {
            registry.register(field, progressField(field));
        }
        registry.register("readingProgress", readingProgress());

        return registry;
    }

    private static SortOrderBuilder<BookEntity> rootField(String field) {
        return ctx -> List.of(order(ctx, ctx.root().get(field)));
    }

    private static SortOrderBuilder<BookEntity> metadataField(String field) {
        return ctx -> List.of(order(ctx, metadataJoin(ctx.root()).get(field)));
    }

    private static SortOrderBuilder<BookEntity> progressField(String field) {
        return ctx -> List.of(order(ctx, progressJoin(ctx).get(field)));
    }

    private static SortOrderBuilder<BookEntity> readingProgress() {
        return ctx -> {
            CriteriaBuilder cb = ctx.cb();
            Join<BookEntity, ?> progress = progressJoin(ctx);
            Expression<Float> greatest = null;
            for (String field : PROGRESS_PERCENT_FIELDS) {
                Expression<Float> value = cb.coalesce(progress.get(field), cb.literal(0f));
                greatest = greatest == null ? value : greatestOf(cb, greatest, value);
            }
            return List.of(order(ctx, greatest));
        };
    }

    private static Expression<Float> greatestOf(CriteriaBuilder cb, Expression<Float> a, Expression<Float> b) {
        return cb.<Float>selectCase()
                .when(cb.greaterThanOrEqualTo(a, b), a)
                .otherwise(b);
    }

    private static Order order(SortContext<BookEntity> ctx, Expression<?> expression) {
        return ctx.descending() ? ctx.cb().desc(expression) : ctx.cb().asc(expression);
    }

    @SuppressWarnings("unchecked")
    private static <Y> Join<BookEntity, Y> metadataJoin(Root<BookEntity> root) {
        for (Join<BookEntity, ?> join : root.getJoins()) {
            if (join.getAttribute().getName().equals("metadata") && join.getJoinType() == JoinType.LEFT) {
                return (Join<BookEntity, Y>) join;
            }
        }
        return root.join("metadata", JoinType.LEFT);
    }

    @SuppressWarnings("unchecked")
    private static Join<BookEntity, ?> progressJoin(SortContext<BookEntity> ctx) {
        Root<BookEntity> root = ctx.root();
        for (Join<BookEntity, ?> join : root.getJoins()) {
            if (join.getAttribute().getName().equals("userBookProgress")) {
                return join;
            }
        }
        Join<BookEntity, ?> join = root.join("userBookProgress", JoinType.LEFT);
        CriteriaBuilder cb = ctx.cb();
        Path<Object> joinUserId = ((Join<BookEntity, Object>) join).get("user").get("id");
        join.on(ctx.userId() != null ? cb.equal(joinUserId, ctx.userId()) : cb.disjunction());
        return join;
    }
}
