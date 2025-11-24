package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathPatternResolverTest {

    @Test
    void testResolvePattern_nullPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, null, "test.pdf");

        assertEquals("test.pdf", result);
    }

    @Test
    void testResolvePattern_blankPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "", "test.pdf");

        assertEquals("test.pdf", result);
    }

    @Test
    void testResolvePattern_whitespacePattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "   ", "test.pdf");

        assertEquals("test.pdf", result);
    }

    @Test
    void testResolvePattern_simpleTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        assertEquals("Test Book.pdf", result);
    }

    @Test
    void testResolvePattern_titleWithExtension() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}.{extension}", "original.pdf");

        assertEquals("Test Book.pdf", result);
    }

    @Test
    void testResolvePattern_multiplePlaceholders() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .authors(Set.of("John Doe", "Jane Smith"))
                .publishedDate(LocalDate.of(2023, 5, 15))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors} - {title} ({year})", "original.pdf");

        // Authors from a Set may be in any order
        assertTrue(result.equals("John Doe, Jane Smith - Test Book (2023).pdf") || 
                   result.equals("Jane Smith, John Doe - Test Book (2023).pdf"));
    }

    @Test
    void testResolvePattern_authorsList() {
        BookMetadata metadata = BookMetadata.builder()
                .authors(Set.of("Author One", "Author Two"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "original.pdf");

        // Authors from a Set may be in any order
        assertTrue(result.equals("Author One, Author Two.pdf") || result.equals("Author Two, Author One.pdf"));
    }

    @Test
    void testResolvePattern_seriesInfo() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("Series Name")
                .seriesNumber(2.0f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{series} #{seriesIndex} - {title}", "original.pdf");

        assertEquals("Series Name #2 - Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_seriesNumberFloat() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("Series Name")
                .seriesNumber(2.5f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{series} #{seriesIndex} - {title}", "original.pdf");

        assertEquals("Series Name #2.5 - Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_optionalBlock_allPresent() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author Name"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< - {authors}>", "original.pdf");

        assertEquals("Book Title - Author Name.pdf", result);
    }

    @Test
    void testResolvePattern_optionalBlock_missingValue() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                // authors is missing/empty
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< - {authors}>", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_isbnPriority() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .isbn13("9781234567890")
                .isbn10("1234567890")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title} - {isbn}", "original.pdf");

        assertEquals("Book Title - 9781234567890.pdf", result);
    }

    @Test
    void testResolvePattern_isbn10Fallback() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .isbn10("1234567890")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title} - {isbn}", "original.pdf");

        assertEquals("Book Title - 1234567890.pdf", result);
    }

    @Test
    void testResolvePattern_nullMetadata() {
        String result = PathPatternResolver.resolvePattern((BookMetadata) null, "{title}", "original.pdf");

        assertEquals("Untitled.pdf", result);
    }

    @Test
    void testResolvePattern_nullTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title(null)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        assertEquals("Untitled.pdf", result);
    }

    @Test
    void testResolvePattern_currentFilename() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{currentFilename}", "original.pdf");

        assertEquals("original.pdf", result);
    }

    @Test
    void testResolvePattern_withBookEntity() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Book Title");
        
        BookEntity book = new BookEntity();
        book.setFileName("book.epub");
        book.setMetadata(metadata);

        String result = PathPatternResolver.resolvePattern(book, "{title}.{extension}");

        assertEquals("Book Title.epub", result);
    }

    @Test
    void testResolvePattern_withBookMetadataEntity() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Book Title");

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_specialCharacters() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book: Title? *With* Special/Chars")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        // Special characters should be sanitized
        assertEquals("Book Title With SpecialChars.pdf", result);
    }

    @Test
    void testResolvePattern_emptyAuthors() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of())
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< - {authors}>", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_handlesNullPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, null, "original.pdf");

        assertEquals("original.pdf", result);
    }

    @Test
    void testResolvePattern_sanitizesIllegalCharacters() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book: The Sequel/Prequel? Illegal*Chars")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        // Should sanitize illegal filesystem characters
        assertNotEquals("Book: The Sequel/Prequel? Illegal*Chars", result);
        assertTrue(result.contains("Book") && result.contains("Sequel"));
    }

    @Test
    void testResolvePattern_handlesMissingMetadataFields() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                // authors is intentionally missing/null
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< - {authors}>", "original.pdf");

        // Optional block should be omitted since authors is missing
        assertEquals("Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_emptyOptionalBlocks() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< [{series}]>< ({year})>", "original.pdf");

        // Both optional blocks should be omitted since series and year are missing
        assertEquals("Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_complexPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("The Great Book")
                .authors(Set.of("John Doe", "Jane Smith"))
                .seriesName("Awesome Series")
                .seriesNumber(3.0f)
                .publishedDate(LocalDate.of(2023, 5, 15))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors} - {title}< [{series} #{seriesIndex}]>< ({year})>", "original.pdf");

        // Authors from a Set may be in any order
        assertTrue(result.equals("John Doe, Jane Smith - The Great Book [Awesome Series #3] (2023).pdf") ||
                   result.equals("Jane Smith, John Doe - The Great Book [Awesome Series #3] (2023).pdf"));
    }
}
