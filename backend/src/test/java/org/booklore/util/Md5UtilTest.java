package org.booklore.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class Md5UtilTest {

    private static final Pattern PATTERN = Pattern.compile("[a-f0-9]{32}");

    @Test
    void testMd5Hex_emptyString() {
        String result = Md5Util.md5Hex("");
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", result);
    }

    @Test
    void testMd5Hex_simpleText() {
        String result = Md5Util.md5Hex("hello");
        assertEquals("5d41402abc4b2a76b9719d911017c592", result);
    }

    @Test
    void testMd5Hex_helloWorld() {
        String result = Md5Util.md5Hex("hello world");
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", result);
    }

    @Test
    void testMd5Hex_numbers() {
        String result = Md5Util.md5Hex("123456789");
        assertEquals("25f9e794323b453885f5181f1b624d0b", result);
    }

    @Test
    void testMd5Hex_specialCharacters() {
        String result = Md5Util.md5Hex("!@#$%^&*()");
        assertEquals("05b28d17a7b6e7024b6e5d8cc43a8bf7", result);
    }

    @Test
    void testMd5Hex_unicode() {
        String result = Md5Util.md5Hex("héllo wörld");
        assertEquals("ed0c22cc110ede12327851863c078138", result);
    }

    @Test
    void testMd5Hex_longText() {
        String longText = "This is a longer text that should produce a consistent MD5 hash regardless of how many times we call the function with the same input.";
        String result1 = Md5Util.md5Hex(longText);
        String result2 = Md5Util.md5Hex(longText);
        assertEquals(result1, result2);
        assertEquals(32, result1.length());
    }

    @Test
    void testMd5Hex_differentInputs() {
        String result1 = Md5Util.md5Hex("hello");
        String result2 = Md5Util.md5Hex("world");
        assertNotEquals(result1, result2);
    }

    @Test
    void testMd5Hex_caseSensitive() {
        String result1 = Md5Util.md5Hex("Hello");
        String result2 = Md5Util.md5Hex("hello");
        assertNotEquals(result1, result2);
    }

    @Test
    void testMd5Hex_nullInput() {
        // MD5Util now handles null input safely by returning null
        String result = Md5Util.md5Hex(null);
        assertNull(result);
    }

    @Test
    void testMd5Hex_length() {
        String result = Md5Util.md5Hex("any input");
        assertEquals(32, result.length()); // MD5 always produces 32 character hex string
        assertTrue(PATTERN.matcher(result).matches()); // Only lowercase hex characters
    }
}
