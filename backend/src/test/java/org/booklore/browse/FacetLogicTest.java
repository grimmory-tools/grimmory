package org.booklore.browse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FacetLogicTest {

    @Test
    void defaultsToAndForNullOrBlank() {
        assertEquals(FacetLogic.AND, FacetLogic.from(null));
        assertEquals(FacetLogic.AND, FacetLogic.from(""));
        assertEquals(FacetLogic.AND, FacetLogic.from("   "));
    }

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertEquals(FacetLogic.OR, FacetLogic.from("or"));
        assertEquals(FacetLogic.OR, FacetLogic.from("OR"));
        assertEquals(FacetLogic.NOT, FacetLogic.from("Not"));
        assertEquals(FacetLogic.AND, FacetLogic.from("and"));
    }

    @Test
    void unknownValueFallsBackToAnd() {
        assertEquals(FacetLogic.AND, FacetLogic.from("xor"));
    }
}
