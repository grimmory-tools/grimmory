package org.booklore.browse;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ParamsHashTest {

    @Test
    void isTwelveCharacters() {
        assertEquals(12, ParamsHash.compute("q", Map.of(), FacetLogic.AND).length());
    }

    @Test
    void isDeterministic() {
        Map<String, List<String>> facets = Map.of("author", List.of("Tolkien"));
        assertEquals(
                ParamsHash.compute("hobbit", facets, FacetLogic.AND),
                ParamsHash.compute("hobbit", facets, FacetLogic.AND));
    }

    @Test
    void isIndependentOfFacetKeyOrder() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A"), "genre", List.of("G")), FacetLogic.AND);
        String b = ParamsHash.compute(null, Map.of("genre", List.of("G"), "author", List.of("A")), FacetLogic.AND);
        assertEquals(a, b);
    }

    @Test
    void isIndependentOfValueOrderWithinAFacet() {
        String a = ParamsHash.compute(null, Map.of("author", List.of("A", "B")), FacetLogic.AND);
        String b = ParamsHash.compute(null, Map.of("author", List.of("B", "A")), FacetLogic.AND);
        assertEquals(a, b);
    }

    @Test
    void queryChangesHash() {
        assertNotEquals(
                ParamsHash.compute("one", Map.of(), FacetLogic.AND),
                ParamsHash.compute("two", Map.of(), FacetLogic.AND));
    }

    @Test
    void facetLogicChangesHash() {
        Map<String, List<String>> facets = Map.of("author", List.of("A"));
        assertNotEquals(
                ParamsHash.compute(null, facets, FacetLogic.AND),
                ParamsHash.compute(null, facets, FacetLogic.OR));
    }

    @Test
    void facetSelectionChangesHash() {
        assertNotEquals(
                ParamsHash.compute(null, Map.of("author", List.of("A")), FacetLogic.AND),
                ParamsHash.compute(null, Map.of("author", List.of("B")), FacetLogic.AND));
    }

    @Test
    void nullQueryAndEmptyFacetsAreStable() {
        assertEquals(
                ParamsHash.compute(null, Map.of(), FacetLogic.AND),
                ParamsHash.compute(null, Map.of(), FacetLogic.AND));
    }

    @Test
    void valuesWithDelimitersDoNotCollide() {
        assertThat(ParamsHash.compute(null, Map.of("author", List.of("A,B")), FacetLogic.AND))
                .isNotEqualTo(ParamsHash.compute(null, Map.of("author", List.of("A", "B")), FacetLogic.AND));
    }
}
