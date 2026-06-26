package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrowsePageTest {

    @Test
    void derivesPageNumberFromOffsetAndLimit() {
        BrowsePage<String> page = BrowsePage.of(List.of("a"), 80, 20, 100, "cur", List.of());
        assertThat(page.page().number()).isEqualTo(4);
        assertThat(page.page().size()).isEqualTo(20);
    }

    @Test
    void derivesTotalPagesWithCeiling() {
        // 101 elements, size 20 -> 6 pages
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 101, "cur", List.of());
        assertThat(page.page().totalPages()).isEqualTo(6);
    }

    @Test
    void exactMultipleTotalPages() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 100, "cur", List.of());
        assertThat(page.page().totalPages()).isEqualTo(5);
    }

    @Test
    void firstPageNumberIsZero() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 100, "cur", List.of());
        assertThat(page.page().number()).isZero();
    }

    @Test
    void emptyResultHasZeroPages() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 20, 0, "cur", List.of());
        assertThat(page.page().totalPages()).isZero();
    }

    @Test
    void zeroLimitDoesNotDivideByZero() {
        BrowsePage<String> page = BrowsePage.of(List.of(), 0, 0, 50, "cur", List.of());
        assertThat(page.page().number()).isZero();
        assertThat(page.page().totalPages()).isZero();
    }

    @Test
    void carriesCursorAndTotals() {
        BrowsePage<String> page = BrowsePage.of(List.of("a", "b"), 0, 20, 42, "the-cursor", List.of());
        assertThat(page.page().cursor()).isEqualTo("the-cursor");
        assertThat(page.page().totalElements()).isEqualTo(42);
    }
}
