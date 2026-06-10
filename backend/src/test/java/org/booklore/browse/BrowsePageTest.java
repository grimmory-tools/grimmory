package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrowsePageTest {

    @Test
    void derivesPageNumberFromOffsetAndLimit() {
        BrowsePage<String> page = BrowsePage.of(List.of("a"), 80, 20, 100, "cur", List.of());
        assertEquals(4, page.page().number());
        assertEquals(20, page.page().size());
    }

    @Test
    void derivesTotalPagesWithCeiling() {
        // 101 elements, size 20 -> 6 pages
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 101, "cur", List.of());
        assertEquals(6, page.page().totalPages());
    }

    @Test
    void exactMultipleTotalPages() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 100, "cur", List.of());
        assertEquals(5, page.page().totalPages());
    }

    @Test
    void firstPageNumberIsZero() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 100, "cur", List.of());
        assertEquals(0, page.page().number());
    }

    @Test
    void emptyResultHasZeroPages() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 0, "cur", List.of());
        assertEquals(0, page.page().totalPages());
    }

    @Test
    void zeroLimitDoesNotDivideByZero() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 0, 50, "cur", List.of());
        assertEquals(0, page.page().number());
        assertEquals(0, page.page().totalPages());
    }

    @Test
    void carriesCursorAndTotals() {
        BrowsePage<String> page = BrowsePage.of(List.of("a", "b"), 0, 20, 42, "the-cursor", List.of());
        assertEquals("the-cursor", page.page().cursor());
        assertEquals(42, page.page().totalElements());
    }
}
