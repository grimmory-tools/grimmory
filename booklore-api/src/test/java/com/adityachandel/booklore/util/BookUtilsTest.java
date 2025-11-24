package com.adityachandel.booklore.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookUtilsTest {

    @Test
    void testCleanFileName_nullInput() {
        String result = BookUtils.cleanFileName(null);
        assertNull(result);
    }

    @Test
    void testCleanFileName_simpleName() {
        String result = BookUtils.cleanFileName("Test Book.pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withZLibrary() {
        String result = BookUtils.cleanFileName("Test Book (Z-Library).pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withAuthorInParentheses() {
        String result = BookUtils.cleanFileName("Test Book (John Doe).pdf");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_withMultipleExtensions() {
        String result = BookUtils.cleanFileName("Test Book.epub.zip");
        assertEquals("Test Book.epub", result);
    }

    @Test
    void testCleanFileName_noExtension() {
        String result = BookUtils.cleanFileName("Test Book");
        assertEquals("Test Book", result);
    }

    @Test
    void testCleanFileName_onlyExtension() {
        // Hidden files (starting with dot) should keep their name to avoid empty strings
        // which could cause DB constraint or filesystem errors
        String result = BookUtils.cleanFileName(".pdf");
        assertEquals(".pdf", result);
    }

    @Test
    void testCleanFileName_complexCase() {
        String result = BookUtils.cleanFileName("Advanced Calculus (Z-Library) (Michael Spivak).pdf");
        assertEquals("Advanced Calculus", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_nullInput() {
        String result = BookUtils.cleanAndTruncateSearchTerm(null);
        assertEquals("", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_emptyString() {
        String result = BookUtils.cleanAndTruncateSearchTerm("");
        assertEquals("", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_simpleText() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Hello World");
        assertEquals("Hello World", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_withSpecialChars() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Hello, World! How are you?");
        assertEquals("Hello World How are you", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_withBrackets() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Test [Book] {Series}");
        assertEquals("Test Book Series", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longText() {
        String longText = "This is a very long search term that should be truncated because it exceeds sixty characters in length and needs to be shortened";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= 60);
        assertEquals("This is a very long search term that should be truncated", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longTextWithSpecialChars() {
        String longText = "This-is,a@very#long$search%term^with&special*chars(that)should[be]truncated{because}it<exceeds>sixty?characters";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= 60);
        assertEquals("Thisisaverylongsearchtermwithspecialcharsthatshouldbetruncat", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_exactly60Chars() {
        String text = "A".repeat(60);
        String result = BookUtils.cleanAndTruncateSearchTerm(text);
        assertEquals(text, result);
        assertEquals(60, result.length());
    }

    @Test
    void testCleanAndTruncateSearchTerm_whitespaceHandling() {
        String result = BookUtils.cleanAndTruncateSearchTerm("  Multiple   Spaces   Here  ");
        assertEquals("Multiple Spaces Here", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_onlySpecialChars() {
        String result = BookUtils.cleanAndTruncateSearchTerm(",.!@#$%^&*()[]{}");
        assertEquals("", result);
    }
}
