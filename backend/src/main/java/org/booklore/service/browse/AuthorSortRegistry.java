package org.booklore.service.browse;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.SortContext;
import org.booklore.browse.SortOrderBuilder;
import org.booklore.browse.SortRegistry;
import org.booklore.model.entity.AuthorEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthorSortRegistry {

    private final AuthorVisibleBooks visibleBooks;

    public SortRegistry<AuthorEntity> registry(BrowseScope scope) {
        SortRegistry<AuthorEntity> registry = new SortRegistry<>();

        registry.register("id", rootField("id"));
        registry.register("name", rootField("name"));
        registry.register("sortName", rootField("sortName"));

        registry.register("bookCount", ctx ->
                List.of(order(ctx, visibleBooks.bookCount(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        registry.register("seriesCount", ctx ->
                List.of(order(ctx, visibleBooks.seriesCount(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        registry.register("addedOn", ctx ->
                List.of(order(ctx, visibleBooks.latestAddedOn(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        registry.register("lastReadTime", ctx ->
                List.of(order(ctx, visibleBooks.lastReadTime(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        registry.register("personalRating", ctx ->
                List.of(order(ctx, visibleBooks.avgPersonalRating(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));

        for (String field : List.of("amazonRating", "goodreadsRating", "hardcoverRating", "ranobedbRating")) {
            registry.register(field, ctx ->
                    List.of(order(ctx, visibleBooks.avgMetadataRating(field, ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        }

        return registry;
    }

    private static SortOrderBuilder<AuthorEntity> rootField(String field) {
        return ctx -> List.of(order(ctx, ctx.root().get(field)));
    }

    private static Order order(SortContext<AuthorEntity> ctx, Expression<?> expression) {
        return ctx.descending() ? ctx.cb().desc(expression) : ctx.cb().asc(expression);
    }
}
