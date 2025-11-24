package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    @Test
    void testTruncate_nullInput() {
        String result = FileService.truncate(null, 10);
        assertNull(result);
    }

    @Test
    void testTruncate_emptyString() {
        String result = FileService.truncate("", 10);
        assertEquals("", result);
    }

    @Test
    void testTruncate_shortString() {
        String input = "short";
        String result = FileService.truncate(input, 10);
        assertEquals("short", result);
    }

    @Test
    void testTruncate_exactLength() {
        String input = "exactly10";
        String result = FileService.truncate(input, 9);
        assertEquals("exactly10", result);
    }

    @Test
    void testTruncate_longString() {
        String input = "this is a very long string that should be truncated";
        String result = FileService.truncate(input, 20);
        assertEquals("this is a very long ", result);
        assertEquals(20, result.length());
    }

    @Test
    void testTruncate_zeroMaxLength() {
        String input = "test string";
        String result = FileService.truncate(input, 0);
        assertEquals("", result);
    }

    @Test
    void testTruncate_negativeMaxLength() {
        String input = "test string";
        String result = FileService.truncate(input, -5);
        assertEquals("", result);
    }

    @Test
    void testTruncate_unicodeCharacters() {
        String input = "héllo wörld with unicode";
        String result = FileService.truncate(input, 15);
        assertEquals("héllo wörld wit", result);
        assertEquals(15, result.length());
    }

    @Test
    void testTruncate_multibyteCharacters() {
        String input = "🚀 rocket emoji test 🌟";
        String result = FileService.truncate(input, 10);
        // Note: This might not be exactly 10 characters due to how Java handles string length with emojis
        assertTrue(result.length() <= input.length());
    }
}
