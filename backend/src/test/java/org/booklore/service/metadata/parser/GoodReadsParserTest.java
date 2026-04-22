package org.booklore.service.metadata.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoodReadsParserTest {

    private final GoodReadsParser parser = new GoodReadsParser(null);

    @ParameterizedTest
    @CsvSource({
            "52555538-dead-simple-python, 52555538",
            "52555538.Dead_Simple_Python, 52555538",
            "52555538.Dead-Simple-Python, 52555538",
            "12345, 12345",
            "12345-abc-123, 12345"
    })
    void extractNumericId_returnsCorrectId(String slug, String expected) {
        assertEquals(expected, parser.extractNumericId(slug));
    }

    @ParameterizedTest
    @CsvSource({
            "nonnumeric",
            "dead-simple-python",
            ".dead-simple-python",
            "-dead-simple-python",
            "''"
    })
    void extractNumericId_returnsNullForInvalidSlugs(String slug) {
        assertNull(parser.extractNumericId(slug));
    }
}
