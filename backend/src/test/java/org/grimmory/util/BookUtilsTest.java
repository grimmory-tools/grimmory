package org.booklore.util;

import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BookUtilsTest {

    private static final int MAX_SEARCH_TERM_LENGTH = 60; // From BookUtils.cleanAndTruncateSearchTerm

    @Test
    void testBuildSearchText() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Harry Potter");
        metadata.setSubtitle("Philosopher's Stone");
        metadata.setSeriesName("Harry Potter Series");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("J.K. Rowling").build()));

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("harry potter"));
        // More permissive: keeps apostrophe
        assertTrue(searchText.contains("philosopher's stone"));
        // Keeps period
        assertTrue(searchText.contains("rowling"));
    }

    @Test
    void testCleanSearchTerm_doesNotTruncate() {
        String longText = "A".repeat(100);
        String result = BookUtils.cleanSearchTerm(longText);
        assertEquals(100, result.length());
        assertEquals(longText, result);
    }

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
        // More permissive: keeps comma
        assertEquals("Hello, World How are you", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_withBrackets() {
        String result = BookUtils.cleanAndTruncateSearchTerm("Test [Book] {Series}");
        // More permissive: keeps brackets and braces
        assertEquals("Test [Book] {Series}", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longText() {
        String longText = "This is a very long search term that should be truncated because it exceeds sixty characters in length and needs to be shortened";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= MAX_SEARCH_TERM_LENGTH, 
            String.format("Result length %d should not exceed max %d", result.length(), MAX_SEARCH_TERM_LENGTH));
        assertEquals("This is a very long search term that should be truncated", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_longTextWithSpecialChars() {
        String longText = "This-is,a@very#long$search%term^with&special*chars(that)should[be]truncated{because}it<exceeds>sixty?characters";
        String result = BookUtils.cleanAndTruncateSearchTerm(longText);
        assertTrue(result.length() <= MAX_SEARCH_TERM_LENGTH,
            String.format("Result length %d should not exceed max %d", result.length(), MAX_SEARCH_TERM_LENGTH));
        assertEquals("This-is,avery#longsearchtermwithspecialchars(that)should[be]", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_exactly60Chars() {
        String text = "A".repeat(MAX_SEARCH_TERM_LENGTH);
        String result = BookUtils.cleanAndTruncateSearchTerm(text);
        assertEquals(text, result);
        assertEquals(MAX_SEARCH_TERM_LENGTH, result.length(), 
            "Text at max length should not be truncated");
    }

    @Test
    void testCleanAndTruncateSearchTerm_whitespaceHandling() {
        String result = BookUtils.cleanAndTruncateSearchTerm("  Multiple   Spaces   Here  ");
        assertEquals("Multiple Spaces Here", result);
    }

    @Test
    void testCleanAndTruncateSearchTerm_onlySpecialChars() {
        String result = BookUtils.cleanAndTruncateSearchTerm(",.!@#$%^&*()[]{}");
        assertEquals(",.#()[]{}", result);
    }

    @Test
    void testNormalizeForSearch() {
        assertEquals("nesbo", BookUtils.normalizeForSearch("Nesbø"));
        assertEquals("jo nesbo", BookUtils.normalizeForSearch("Jo Nesbø"));
        assertEquals("aeiou", BookUtils.normalizeForSearch("áéíóú"));
        assertEquals("aeiou", BookUtils.normalizeForSearch("ÀÈÌÒÙ"));
        assertEquals("l", BookUtils.normalizeForSearch("ł"));
        assertEquals("ss", BookUtils.normalizeForSearch("ß"));
        assertEquals("harry potter", BookUtils.normalizeForSearch("Harry Potter"));
        assertEquals("misere", BookUtils.normalizeForSearch("Misère"));
    }

    @Test
    void testNormalizeForSearch_variousDiacritics() {
        // French
        assertEquals("francois", BookUtils.normalizeForSearch("François"));
        assertEquals("renee", BookUtils.normalizeForSearch("Renée"));
        assertEquals("helene", BookUtils.normalizeForSearch("Hélène"));
        
        // Spanish
        assertEquals("jose", BookUtils.normalizeForSearch("José"));
        assertEquals("nino", BookUtils.normalizeForSearch("Niño"));
        assertEquals("manana", BookUtils.normalizeForSearch("Mañana"));
        
        // German
        assertEquals("muller", BookUtils.normalizeForSearch("Müller"));
        assertEquals("gross", BookUtils.normalizeForSearch("Groß"));
        assertEquals("schon", BookUtils.normalizeForSearch("Schön"));
        
        // Polish
        assertEquals("lodz", BookUtils.normalizeForSearch("Łódź"));
        assertEquals("wroclaw", BookUtils.normalizeForSearch("Wrocław"));
        
        // Scandinavian
        assertEquals("oslo", BookUtils.normalizeForSearch("Oslø"));
        assertEquals("malmo", BookUtils.normalizeForSearch("Malmö"));
        assertEquals("copenhagen", BookUtils.normalizeForSearch("Cøpenhagen"));
        
        // Portuguese
        assertEquals("sao paulo", BookUtils.normalizeForSearch("São Paulo"));
    }
    
    @Test
    void testNormalizeForSearch_programmingLanguages() {
        // Test that + and # are preserved for programming book titles
        assertEquals("c++", BookUtils.normalizeForSearch("C++"));
        assertEquals("c#", BookUtils.normalizeForSearch("C#"));
        assertEquals("f#", BookUtils.normalizeForSearch("F#"));
        assertEquals("effective c++ programming", BookUtils.normalizeForSearch("Effective C++ Programming"));
        assertEquals("c# in depth", BookUtils.normalizeForSearch("C# In Depth"));
        assertEquals("cacao", BookUtils.normalizeForSearch("Cação"));
        
        // Turkish
        assertEquals("istanbul", BookUtils.normalizeForSearch("İstanbul"));
        
        // Czech
        assertEquals("dvorak", BookUtils.normalizeForSearch("Dvořák"));
        assertEquals("capek", BookUtils.normalizeForSearch("Čapek"));
    }

    @Test
    void testBuildSearchText_withDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Snowman");
        metadata.setSubtitle("A Harry Hole Novel");
        metadata.setSeriesName("Harry Hole");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("Jo Nesbø").build()));

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("jo nesbo"), "Expected 'jo nesbo' in: " + searchText);
        assertTrue(searchText.contains("the snowman"), "Expected 'the snowman' in: " + searchText);
        assertTrue(searchText.contains("harry hole"), "Expected 'harry hole' in: " + searchText);
        
        assertFalse(searchText.contains("ø"), "Should not contain 'ø' in: " + searchText);
        assertFalse(searchText.contains("Nesbø"), "Should not contain 'Nesbø' in: " + searchText);
    }

    @Test
    void testSearchMatchingWithAndWithoutDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Misère");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("François Müller").build()));
        
        String storedSearchText = BookUtils.buildSearchText(metadata);
        
        String searchWithoutDiacritics = BookUtils.normalizeForSearch("francois muller");
        String searchWithDiacritics = BookUtils.normalizeForSearch("François Müller");
        String searchMixedCase = BookUtils.normalizeForSearch("FRANCOIS muller");
        
        assertEquals(searchWithoutDiacritics, searchWithDiacritics);
        assertEquals(searchWithoutDiacritics, searchMixedCase);
        
        assertTrue(storedSearchText.contains("francois muller"),
            "Stored text should contain normalized author: " + storedSearchText);
        
        assertTrue(storedSearchText.contains(searchWithoutDiacritics),
            "Search without diacritics should match");
        assertTrue(storedSearchText.contains(searchWithDiacritics), 
            "Search with diacritics should match");
    }

    @Test
    void testNormalizeForSearch_nullAndEmpty() {
        assertNull(BookUtils.normalizeForSearch(null));
        assertEquals("", BookUtils.normalizeForSearch(""));
        assertEquals("", BookUtils.normalizeForSearch("   "));
    }

    @Test
    void testNormalizeForSearch_preservesSpaces() {
        assertEquals("jo nesbo book", BookUtils.normalizeForSearch("Jo Nesbø Book"));
        assertEquals("multiple word title", BookUtils.normalizeForSearch("Multiple Word Title"));
    }

    @Test
    void testNormalizeForSearch_removesSpecialCharacters() {
        assertEquals("book: title", BookUtils.normalizeForSearch("Book: Title!"));
        assertEquals("author's name", BookUtils.normalizeForSearch("Author's Name"));
        assertEquals("test (123)", BookUtils.normalizeForSearch("Test (123)"));
    }

    @Test
    void testBuildSearchText_withNullFields() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Title Only");

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertEquals("title only", searchText);
    }

    @Test
    void testBuildSearchText_handlesExceptionGracefully() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("test book"));
    }

    @Test
    void testBuildSearchText_withAuthorHavingNullName() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        List<AuthorEntity> authors = new ArrayList<>();
        authors.add(AuthorEntity.builder().name("Valid Author").build());
        authors.add(AuthorEntity.builder().name(null).build()); // Author with null name
        metadata.setAuthors(authors);
        
        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("valid author"));
        assertTrue(searchText.contains("test book"));
    }

    @Test
    void testBuildSearchText_withEmptyAuthorsSet() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        metadata.setAuthors(new ArrayList<>()); // Empty list
        
        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertEquals("test book", searchText);
    }

    @Test
    void testBuildSearchText_withNullMetadata() {
        String result = BookUtils.buildSearchText(null);
        assertNull(result);
    }

    @Test
    void testBuildSearchText_withAllNullFields() {
        BookMetadataEntity metadata = new BookMetadataEntity();

        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
    }

    @Test
    void testBuildSearchText_withNullAuthors() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Book");
        metadata.setAuthors(null); // null authors set
        
        String searchText = BookUtils.buildSearchText(metadata);
        
        assertNotNull(searchText);
        assertTrue(searchText.contains("test book"));
    }

    @Test
    void testCleanAndTruncateSearchTerm_singleLongWord() {
        String longWord = "A".repeat(100);
        String result = BookUtils.cleanAndTruncateSearchTerm(longWord);
        
        assertEquals(MAX_SEARCH_TERM_LENGTH, result.length(), 
            "Single long word should be truncated to max length");
    }

    @Test
    void testCleanAndTruncateSearchTerm_exactly60CharsWithWords() {
        String text = "word ".repeat(12); // 60 chars exactly
        String result = BookUtils.cleanAndTruncateSearchTerm(text);
        
        assertEquals(text.trim(), result);
    }

    @ParameterizedTest
    @CsvSource({
        "'Book (Author) (Year) (Z-Library).pdf', 'Book'",
        "'Book (Z-Library) More Text.pdf', 'Book More Text'",
        "'Book ((Nested)) (Author).pdf', 'Book'",
        "'Simple Book.epub', 'Simple Book'",
        "'Book (Author Name).pdf', 'Book'",
        "'Complex (Nested (Deep)) (Multiple) File.epub', 'Complex File'"
    })
    void testCleanFileName_parameterized(String input, String expected) {
        String result = BookUtils.cleanFileName(input);
        assertEquals(expected, result, 
            String.format("Input: '%s' should result in: '%s'", input, expected));
    }

    @Test
    void testCleanFileName_withMultipleParentheses() {
        String result = BookUtils.cleanFileName("Book (Author) (Year) (Z-Library).pdf");
        assertEquals("Book", result);
    }

    @Test
    void testCleanFileName_withNestedParentheses() {
        String result = BookUtils.cleanFileName("Book ((Nested)) (Author).pdf");
        assertEquals("Book", result);
    }

    @Test
    void testCleanFileName_withZLibraryInMiddle() {
        String result = BookUtils.cleanFileName("Book (Z-Library) More Text.pdf");
        assertEquals("Book More Text", result);
    }

    @Test
    void testCleanSearchTerm_withOnlySpecialChars() {
        String result = BookUtils.cleanSearchTerm("!@#$%^&*()");
        // Pattern [!@$%^&*_=|~`<>?/\"] doesn't include # or (), so they remain
        // After removing matched chars and trimming, we get "#()"
        assertEquals("#()", result);
    }

    @Test
    void testCleanSearchTerm_withMixedSpecialChars() {
        String result = BookUtils.cleanSearchTerm("Hello! World@ Test#");
        assertEquals("Hello World Test#", result);
    }

    @Test
    void testCleanSearchTerm_removesSpecialCharacters() {
        String input = "Harry Potter: The #1 Sorcerer's Stone!";
        String result = BookUtils.cleanSearchTerm(input);
        
        assertFalse(result.contains("!"), "Should remove exclamation mark (in pattern)");
        assertTrue(result.contains(":"), "Colon is not in removal pattern, so it remains");
        assertTrue(result.contains("Harry Potter"), "Should preserve text content");
        assertTrue(result.contains("#1"), "Should preserve # (not in removal pattern)");
    }

    @Test
    void testNormalizeForSearch_withEmptyString() {
        String result = BookUtils.normalizeForSearch("");
        assertEquals("", result);
    }

    @Test
    void testNormalizeForSearch_withOnlyWhitespace() {
        String result = BookUtils.normalizeForSearch("   ");
        assertEquals("", result);
    }

    @Test
    void testNormalizeForSearch_preservesNumbers() {
        assertEquals("12345", BookUtils.normalizeForSearch("12345"));
        assertEquals("book 123", BookUtils.normalizeForSearch("Book 123"));
    }

    @Test
    void testNormalizeForSearch_handlesMixedCaseDiacritics() {
        assertEquals("francois", BookUtils.normalizeForSearch("François"));
        assertEquals("francois", BookUtils.normalizeForSearch("FRANÇOIS"));
    }
}
