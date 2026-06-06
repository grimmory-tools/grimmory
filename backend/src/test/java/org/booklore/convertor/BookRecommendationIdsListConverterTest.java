package org.booklore.convertor;

import org.booklore.model.dto.BookRecommendationLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookRecommendationIdsListConverterTest {

    private BookRecommendationIdsListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BookRecommendationIdsListConverter();
    }

    @Test
    void convertToDatabaseColumn_shouldSerializeSetToJsonString() {
        BookRecommendationLite rec1 = new BookRecommendationLite(1L, 0.95);
        BookRecommendationLite rec2 = new BookRecommendationLite(2L, 0.87);

        Set<BookRecommendationLite> input = Set.of(rec1, rec2);

        String result = converter.convertToDatabaseColumn(input);

        assertNotNull(result);
        assertTrue(result.contains("\"b\":1"));
        assertTrue(result.contains("\"s\":0.95"));
        assertTrue(result.contains("\"b\":2"));
        assertTrue(result.contains("\"s\":0.87"));
    }

    @Test
    void convertToDatabaseColumn_withNull_shouldReturnNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }

    @Test
    void convertToEntityAttribute_shouldDeserializeJsonStringToSet() {
        String json = "[{\"b\":1,\"s\":0.95},{\"b\":2,\"s\":0.87}]";

        Set<BookRecommendationLite> result = converter.convertToEntityAttribute(json);

        assertNotNull(result);
        assertEquals(2, result.size());

        BookRecommendationLite book1 = result.stream()
                .filter(b -> b.getB() == 1L)
                .findFirst()
                .orElse(null);
        assertNotNull(book1);
        assertEquals(0.95, book1.getS(), 0.001);

        BookRecommendationLite book2 = result.stream()
                .filter(b -> b.getB() == 2L)
                .findFirst()
                .orElse(null);
        assertNotNull(book2);
        assertEquals(0.87, book2.getS(), 0.001);
    }

    @Test
    void convertToEntityAttribute_withNull_shouldReturnEmptySet() {
        Set<BookRecommendationLite> result = converter.convertToEntityAttribute(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void convertToEntityAttribute_withEmptyString_shouldReturnEmptySet() {
        Set<BookRecommendationLite> result = converter.convertToEntityAttribute("");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void convertToEntityAttribute_withBlankString_shouldReturnEmptySet() {
        Set<BookRecommendationLite> result = converter.convertToEntityAttribute("   ");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
