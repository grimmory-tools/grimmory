package org.booklore.service.browse;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.SortContext;
import org.booklore.browse.SortOrderBuilder;
import org.booklore.browse.SortRegistry;
import org.booklore.model.entity.AuthorEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthorSortRegistry {

    private final AuthorVisibleBooks visibleBooks;

    public SortRegistry<AuthorEntity> registry(BrowseScope scope) {
        Map<String, SortOrderBuilder<AuthorEntity>> builders = new LinkedHashMap<>();

        builders.put("id", rootField("id"));
        builders.put("name", rootField("name"));
        builders.put("sortName", rootField("sortName"));

        builders.put("bookCount", ctx ->
                List.of(order(ctx, visibleBooks.bookCount(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        builders.put("seriesCount", ctx ->
                List.of(order(ctx, visibleBooks.seriesCount(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        builders.put("addedOn", ctx ->
                List.of(order(ctx, visibleBooks.latestAddedOn(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        builders.put("lastReadTime", ctx ->
                List.of(order(ctx, visibleBooks.lastReadTime(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        builders.put("personalRating", ctx ->
                List.of(order(ctx, visibleBooks.avgPersonalRating(ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));

        for (String field : List.of("amazonRating", "goodreadsRating", "hardcoverRating", "ranobedbRating")) {
            builders.put(field, ctx ->
                    List.of(order(ctx, visibleBooks.avgMetadataRating(field, ctx.root().get("id"), ctx.query(), ctx.cb(), scope))));
        }

        return new SortRegistry<>(builders);
    }

    private static SortOrderBuilder<AuthorEntity> rootField(String field) {
        return ctx -> List.of(order(ctx, ctx.root().get(field)));
    }

    private static Order order(SortContext<AuthorEntity> ctx, Expression<?> expression) {
        return ctx.descending() ? ctx.cb().desc(expression) : ctx.cb().asc(expression);
    }
}
