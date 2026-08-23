package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParamsHashTest {

    @Test
    void isTwelveCharacters() {
        assertThat(ParamsHash.compute("q", Map.of())).hasSize(12);
    }

    @Test
    void isDeterministic() {
        Map<String, List<String>> facets = Map.of("author", List.of("Tolkien"));
        assertThat(ParamsHash.compute("hobbit", facets))
                .isEqualTo(ParamsHash.compute("hobbit", facets));
    }

    @Test
    void isIndependentOfFacetKeyOrder() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A"), "genre", List.of("G")));
        String b = ParamsHash.compute(null, Map.of("genre", List.of("G"), "author", List.of("A")));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void isIndependentOfValueOrderWithinAFacet() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A", "B")));
        String b = ParamsHash.compute(null, Map.of("author", List.of("B", "A")));
        assertThat(a).isEqualTo(b);
    }

    @Test
    void queryChangesHash() {
        assertThat(ParamsHash.compute("one", Map.of()))
                .isNotEqualTo(ParamsHash.compute("two", Map.of()));
    }

    @Test
    void facetSelectionChangesHash() {
        assertThat(ParamsHash.compute(null, Map.of("author", List.of("A"))))
                .isNotEqualTo(ParamsHash.compute(null, Map.of("author", List.of("B"))));
    }

    @Test
    void nullQueryAndEmptyFacetsAreStable() {
        assertThat(ParamsHash.compute(null, Map.of()))
                .isEqualTo(ParamsHash.compute(null, Map.of()));
    }

    @Test
    void valuesWithDelimitersDoNotCollide() {
        assertThat(ParamsHash.compute(null, Map.of("author", List.of("A,B"))))
                .isNotEqualTo(ParamsHash.compute(null, Map.of("author", List.of("A", "B"))));
    }
}
