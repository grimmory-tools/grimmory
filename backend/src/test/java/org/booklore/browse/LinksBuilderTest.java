package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinksBuilderTest {

    private final CursorCodec codec = new CursorCodec();
    private final LinksBuilder builder = new LinksBuilder(codec);

    private LinksBuilder.Context context(long offset, int limit, long total, String preservedQuery) {
        CursorState base = new CursorState(offset, limit, "title", "hash00000000");
        return new LinksBuilder.Context("/api/v1/books/page", preservedQuery, offset, limit, total, base);
    }

    private boolean hasRel(List<Link> links, String rel) {
        return links.stream().anyMatch(l -> l.rel().contains(rel));
    }

    private Link withRel(List<Link> links, String rel) {
        Optional<Link> found = links.stream().filter(l -> l.rel().contains(rel)).findFirst();
        assertTrue(found.isPresent(), "expected a link with rel " + rel);
        return found.get();
    }

    @Test
    void selfAndFirstAlwaysPresent() {
        List<Link> links = builder.build(context(40, 20, 100, ""));
        assertTrue(hasRel(links, "self"));
        assertTrue(hasRel(links, "first"));
    }

    @Test
    void firstPageOmitsPreviousButHasNext() {
        List<Link> links = builder.build(context(0, 20, 100, ""));
        assertFalse(hasRel(links, "previous"));
        assertTrue(hasRel(links, "next"));
    }

    @Test
    void middlePageHasPreviousAndNext() {
        List<Link> links = builder.build(context(40, 20, 100, ""));
        assertTrue(hasRel(links, "previous"));
        assertTrue(hasRel(links, "next"));
    }

    @Test
    void lastPageOmitsNext() {
        List<Link> links = builder.build(context(80, 20, 100, ""));
        assertTrue(hasRel(links, "previous"));
        assertFalse(hasRel(links, "next"));
    }

    @Test
    void singlePageOmitsBothPreviousAndNext() {
        List<Link> links = builder.build(context(0, 20, 12, ""));
        assertFalse(hasRel(links, "previous"));
        assertFalse(hasRel(links, "next"));
    }

    @Test
    void emptyResultHasNoPreviousOrNext() {
        List<Link> links = builder.build(context(0, 20, 0, ""));
        assertFalse(hasRel(links, "previous"));
        assertFalse(hasRel(links, "next"));
        assertTrue(hasRel(links, "self"));
    }

    @Test
    void pagingLinksCarryPreservedQueryAndCursor() {
        Link next = withRel(builder.build(context(0, 20, 100, "facet=genre%3AFiction")), "next");
        assertTrue(next.href().startsWith("/api/v1/books/page?facet=genre%3AFiction&cursor="));
    }

    @Test
    void nextCursorAdvancesByLimit() {
        Link next = withRel(builder.build(context(0, 20, 100, "")), "next");
        String cursor = next.href().substring(next.href().indexOf("cursor=") + "cursor=".length());
        assertEquals(20, codec.decode(cursor).offset());
    }

    @Test
    void previousCursorClampsAtZero() {
        // offset 10, limit 20 -> previous offset clamps to 0
        Link prev = withRel(builder.build(context(10, 20, 100, "")), "previous");
        String cursor = prev.href().substring(prev.href().indexOf("cursor=") + "cursor=".length());
        assertEquals(0, codec.decode(cursor).offset());
    }

    @Test
    void selfCursorReflectsCurrentOffset() {
        Link self = withRel(builder.build(context(40, 20, 100, "")), "self");
        String cursor = self.href().substring(self.href().indexOf("cursor=") + "cursor=".length());
        assertEquals(40, codec.decode(cursor).offset());
    }

    @Test
    void preservedQueryAbsentWhenBlank() {
        Predicate<Link> isPageLink = l -> l.href().startsWith("/api/v1/books/page");
        builder.build(context(0, 20, 100, "")).stream().filter(isPageLink)
                .forEach(l -> assertTrue(l.href().startsWith("/api/v1/books/page?cursor=")));
    }

    @Test
    void nullPreservedQueryStillProducesValidLinks() {
        List<Link> links = builder.build(new LinksBuilder.Context(
                "/api/v1/books/page", null, 0, 20, 5,
                new CursorState(0, 20, "title", "hash00000000")));
        assertTrue(hasRel(links, "self"));
        Link self = withRel(links, "self");
        assertTrue(self.href().startsWith("/api/v1/books/page?cursor="));
    }
}
