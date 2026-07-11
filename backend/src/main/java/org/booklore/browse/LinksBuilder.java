package org.booklore.browse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Builds the paging links for a browse page (self/first/previous/next)
// Each carries the request's facet/query params + a cursor offset
@Component
public class LinksBuilder {

    private final CursorCodec codec;

    public LinksBuilder(CursorCodec codec) {
        this.codec = codec;
    }

    public record Context(
            String pagePath,
            String preservedQuery,
            long offset,
            int limit,
            long totalElements,
            CursorState baseState
    ) {
    }

    public List<Link> build(Context ctx) {
        List<Link> links = new ArrayList<>();
        links.add(pageLink(List.of("self"), ctx, ctx.offset()));
        links.add(pageLink(List.of("first"), ctx, 0));

        if (ctx.offset() > 0) {
            long previousOffset = Math.max(0, ctx.offset() - ctx.limit());
            links.add(pageLink(List.of("previous"), ctx, previousOffset));
        }
        if (ctx.offset() + ctx.limit() < ctx.totalElements()) {
            links.add(pageLink(List.of("next"), ctx, ctx.offset() + ctx.limit()));
        }
        return links;
    }

    private Link pageLink(List<String> rel, Context ctx, long offset) {
        String cursor = codec.encode(ctx.baseState().withOffset(offset));
        StringBuilder href = new StringBuilder(ctx.pagePath()).append('?');
        if (ctx.preservedQuery() != null && !ctx.preservedQuery().isBlank()) {
            href.append(ctx.preservedQuery()).append('&');
        }
        href.append("cursor=").append(cursor);
        return Link.json(rel, href.toString());
    }
}
