package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParamsHashTest {

    @Test
    void isTwelveCharacters() {
        assertThat(ParamsHash.compute("q", Map.of(), FacetLogic.AND)).hasSize(12);
    }

    @Test
    void isDeterministic() {
        Map<String, List<String>> facets = Map.of("author", List.of("Tolkien"));
        assertThat(ParamsHash.compute("hobbit", facets, FacetLogic.AND))
                .isEqualTo(ParamsHash.compute("hobbit", facets, FacetLogic.AND));
    }

    @Test
    void isIndependentOfFacetKeyOrder() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A"), "genre", List.of("G")), FacetLogic.AND);
        String b = ParamsHash.compute(null, Map.of("genre", List.of("G"), "author", List.of("A")), FacetLogic.AND);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void isIndependentOfValueOrderWithinAFacet() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A", "B")), FacetLogic.AND);
        String b = ParamsHash.compute(null, Map.of("author", List.of("B", "A")), FacetLogic.AND);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void queryChangesHash() {
        assertThat(ParamsHash.compute("one", Map.of(), FacetLogic.AND))
                .isNotEqualTo(ParamsHash.compute("two", Map.of(), FacetLogic.AND));
    }

    @Test
    void facetLogicChangesHash() {
        Map<String, List<String>> facets = Map.of("author", List.of("A"));
        assertThat(ParamsHash.compute(null, facets, FacetLogic.AND))
                .isNotEqualTo(ParamsHash.compute(null, facets, FacetLogic.OR));
    }

    @Test
    void facetSelectionChangesHash() {
        assertThat(ParamsHash.compute(null, Map.of("author", List.of("A")), FacetLogic.AND))
                .isNotEqualTo(ParamsHash.compute(null, Map.of("author", List.of("B")), FacetLogic.AND));
    }

    @Test
    void nullQueryAndEmptyFacetsAreStable() {
        assertThat(ParamsHash.compute(null, Map.of(), FacetLogic.AND))
                .isEqualTo(ParamsHash.compute(null, Map.of(), FacetLogic.AND));
    }

    @Test
    void valuesWithDelimitersDoNotCollide() {
        assertThat(ParamsHash.compute(null, Map.of("author", List.of("A,B")), FacetLogic.AND))
                .isNotEqualTo(ParamsHash.compute(null, Map.of("author", List.of("A", "B")), FacetLogic.AND));
    }
}
