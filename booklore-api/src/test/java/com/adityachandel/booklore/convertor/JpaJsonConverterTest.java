package com.adityachandel.booklore.convertor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JpaJsonConverterTest {

    private JpaJsonConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JpaJsonConverter();
    }

    @Test
    void convertToDatabaseColumn_shouldSerializeMapToJsonString() {
        Map<String, Object> input = Map.of(
                "key1", "value1",
                "key2", 42,
                "key3", true
        );

        String result = converter.convertToDatabaseColumn(input);

        assertNotNull(result);
        assertTrue(result.contains("\"key1\":\"value1\""));
        assertTrue(result.contains("\"key2\":42"));
        assertTrue(result.contains("\"key3\":true"));
    }

    @Test
    void convertToDatabaseColumn_withNull_shouldReturnNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }

    @Test
    void convertToEntityAttribute_shouldDeserializeJsonStringToMap() {
        String json = "{\"key1\":\"value1\",\"key2\":42,\"key3\":true}";
        Map<String, Object> expected = Map.of(
                "key1", "value1",
                "key2", 42,
                "key3", true
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
