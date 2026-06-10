package org.booklore.browse;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the OPDS-style links array for a browse page: self, first, previous (omitted on the
 * first page), next (omitted on the last page), and a facet link to the facets endpoint.
 * Paging links carry the request's facet/query parameters and a cursor derived by re-offsetting
 * the base cursor state. The facets live in the URL (not only the cursor) so each link is
 * independently re-runnable; the cursor's params hash guards against tampering.
 */
@Component
public class LinksBuilder {

    private final CursorCodec codec;

    public LinksBuilder(CursorCodec codec) {
        this.codec = codec;
    }

    /**
     * Inputs for building a page's links.
     *
     * @param pagePath       the page endpoint path (e.g. /books/page)
     * @param facetPath      the facets endpoint path (e.g. /books/facets)
     * @param preservedQuery the URL-encoded facet/query parameters to carry on paging links, without leading '?'; may be blank
     * @param offset         the current page's offset
     * @param limit          the current page's size
     * @param totalElements  the total matching element count
     * @param baseState      the cursor state to re-offset for each link
     */
    public record Context(
            String pagePath,
            String facetPath,
            String preservedQuery,
            long offset,
            int limit,
            long totalElements,
            CursorState baseState
    ) {
    }

    public List<Link> build(Context ctx) {
        List<Link> links = new ArrayList<>();
        links.add(Link.json(List.of("facet"), ctx.facetPath()));
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
