package org.booklore.convertor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapToStringConverterTest {

    private MapToStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MapToStringConverter();
    }

    @Test
    void convertToDatabaseColumn_shouldSerializeMapToJsonString() {
        Map<String, Object> input = Map.of(
                "title", "Test Book",
                "author", "Test Author",
                "year", 2023
        );

        String result = converter.convertToDatabaseColumn(input);
        assertNotNull(result);
        assertTrue(result.contains("\"title\":\"Test Book\""));
        assertTrue(result.contains("\"author\":\"Test Author\""));
        assertTrue(result.contains("\"year\":2023"));
    }

    @Test
    void convertToDatabaseColumn_withNull_shouldReturnNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }

    @Test
    void convertToEntityAttribute_shouldDeserializeJsonStringToMap() {
        String json = "{\"title\":\"Test Book\",\"author\":\"Test Author\",\"year\":2023}";
        Map<String, Object> expected = Map.of(
                "title", "Test Book",
                "author", "Test Author",
                "year", 2023
        );

        Map<String, Object> result = converter.convertToEntityAttribute(json);

        assertNotNull(result);
        assertEquals(expected, result);
    }

    @Test
    void convertToEntityAttribute_withNull_shouldReturnNull() {
        Map<String, Object> result = converter.convertToEntityAttribute(null);

        assertNull(result);
    }

    @Test
    void convertToEntityAttribute_withEmptyString_shouldReturnNull() {
        Map<String, Object> result = converter.convertToEntityAttribute("");

        assertNull(result);
    }

    @Test
    void convertToEntityAttribute_withBlankString_shouldReturnNull() {
        Map<String, Object> result = converter.convertToEntityAttribute("   ");

        assertNull(result);
    }
}
