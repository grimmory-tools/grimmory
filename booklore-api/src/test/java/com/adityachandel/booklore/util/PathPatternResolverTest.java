package com.adityachandel.booklore.util;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathPatternResolverTest {

    public static final Set<String> LONG_AUTHOR_LIST = new LinkedHashSet<>(List.of(
        "梁思成", "叶嘉莹", "厉以宁", "萧乾", "冯友兰", "费孝通", "李济", "侯仁之", "汤一介", "温源宁",
        "胡适", "吴青", "李照国", "蒋梦麟", "汪荣祖", "邢玉瑞", "《中华思想文化术语》编委会",
        "北京大学政策法规研究室", "（美）艾恺（Guy S. Alitto）", "顾毓琇", "陈从周",
        "（加拿大）伊莎白（Isabel Crook）（美）柯临清（Christina Gilmartin）", "傅莹"
    ));

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

        assertEquals("original.pdf", result);
    }

    @Test
    void testResolvePattern_nullTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title(null)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        assertEquals("original.pdf", result);
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

    @Test
    @DisplayName("Should truncate long author lists to prevent filesystem errors")
    void testResolvePattern_truncatesLongAuthorList() {
        BookMetadata metadata = BookMetadata.builder()
                .title("中国文化合集")
                .authors(LONG_AUTHOR_LIST)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}/{title}", "original.epub");

        assertTrue(result.contains("中国文化合集"), "Should contain the title");
        assertTrue(result.endsWith(".epub"), "Should end with file extension");

        String[] pathComponents = result.split("/");
        for (String component : pathComponents) {
            int byteLength = component.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(byteLength <= 245,
                "Component '" + component + "' byte length should not exceed filesystem limits: " + byteLength);
        }

        // Verify the authors part is properly truncated by bytes
        String authorsPart = pathComponents[0];
        int authorsBytes = authorsPart.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(authorsBytes <= 180, "Authors part should be truncated to <= 180 bytes: " + authorsBytes);
    }

    @Test
    void testResolvePattern_authorsWithinLimit() {
        Set<String> authors = Set.of("John Doe", "Jane Smith", "Bob Wilson");

        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .authors(authors)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "original.pdf");

        assertTrue(result.contains("John Doe") && result.contains("Jane Smith") && result.contains("Bob Wilson"));
        assertTrue(result.endsWith(".pdf"));
    }

    @Test
    @DisplayName("Should apply author truncation in various pattern contexts")
    void testResolvePattern_appliesAuthorTruncation() {
        Set<String> shortAuthorList = new LinkedHashSet<>(List.of("John Doe", "Jane Smith"));

        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .authors(shortAuthorList)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "test.epub");

        assertTrue(result.endsWith(".epub"));
        String authorsPart = result.replace(".epub", "");
        int authorsBytes = authorsPart.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(authorsBytes <= 180, "Authors should be <= 180 bytes: " + authorsBytes);

        BookMetadata longMetadata = BookMetadata.builder()
                .title("Test")
                .authors(LONG_AUTHOR_LIST)
                .build();

        String longResult = PathPatternResolver.resolvePattern(longMetadata, "{authors}", "test.epub");

        String longAuthorsPart = longResult.replace(".epub", "");
        int longAuthorsBytes = longAuthorsPart.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(longAuthorsBytes <= 180, "Long authors should be truncated to <= 180 bytes, got: " + longAuthorsBytes);

        assertTrue(longAuthorsBytes < LONG_AUTHOR_LIST.toString().getBytes(StandardCharsets.UTF_8).length,
            "Truncated result should be shorter than original long author list");
    }

    @Test
    @DisplayName("Should handle single author that exceeds byte limits")
    void testResolvePattern_truncatesSingleVeryLongAuthor() {
        String veryLongAuthor = "某某某某某某某某某某".repeat(10); // ~300 bytes in UTF-8

        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .authors(Set.of(veryLongAuthor))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "test.epub");

        String authorsPart = result.replace(".epub", "");
        int authorsBytes = authorsPart.getBytes(StandardCharsets.UTF_8).length;

        assertTrue(authorsBytes <= 180,
            "Single long author should be truncated to <= 180 bytes: " + authorsBytes);
        assertFalse(authorsPart.isEmpty(), "Should not be empty after truncation");
        assertTrue(authorsBytes < veryLongAuthor.getBytes(StandardCharsets.UTF_8).length,
            "Truncated result should be shorter than original single long author");
    }

    @Test
    @DisplayName("Should add 'et al.' when authors are truncated")
    void testResolvePattern_addsEtAlWhenTruncated() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .authors(LONG_AUTHOR_LIST)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "test.epub");

        assertTrue(result.contains("et al."), "Should contain truncation indicator when authors are truncated");
    }

    @Test
    @DisplayName("Should truncate combined long components in final validation")
    void testResolvePattern_validatesFinalPathWithCombinedLongComponents() {
        String longTitle = "某".repeat(70); // ~210 bytes

        BookMetadata metadata = BookMetadata.builder()
                .title(longTitle)
                .authors(LONG_AUTHOR_LIST)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title} - {authors}", "test.epub");

        String[] components = result.split("/");
        for (String component : components) {
            if (!component.contains(".")) { // Skip filename with extension
                int byteLength = component.getBytes(StandardCharsets.UTF_8).length;
                assertTrue(byteLength <= 245, "Path component should be <= 245 bytes: " + byteLength + " for component: " + component);
            }
        }
    }

    @Test
    @DisplayName("Should preserve file extension when truncating very long filenames")
    void testResolvePattern_preservesExtensionOnTruncation() {
        String longTitle = "A".repeat(300); // 300 bytes

        BookMetadata metadata = BookMetadata.builder().title(longTitle).build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "original.pdf");

        assertTrue(result.endsWith(".pdf"), "Extension must be preserved");
        assertTrue(result.length() < 300, "Filename must be truncated");

        int byteLen = result.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(byteLen <= 245, "Total filename bytes " + byteLen + " should be <= 245");
    }

    @Test
    @DisplayName("Should remove trailing dots from path components for Windows compatibility")
    void testResolvePattern_removesTrailingDots() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author Name Jr."))
                .build();

        // Pattern: {authors}/{title}
        String result = PathPatternResolver.resolvePattern(metadata, "{authors}/{title}", "original.pdf");

        // Expected: Author Name Jr/Book Title.pdf
        // Windows does not allow folder names ending in '.'
        // So "Author Name Jr." should become "Author Name Jr"

        String[] components = result.split("/");
        assertTrue(components.length >= 1);

        String authorDir = components[0];
        assertFalse(authorDir.endsWith("."), "Directory name should not end with a dot: " + authorDir);
        assertTrue(authorDir.equals("Author Name Jr"), "Expected 'Author Name Jr' but got '" + authorDir + "'");
    }

    @Test
    @DisplayName("Should remove trailing dots from multiple path components")
    void testResolvePattern_removesTrailingDotsFromMultipleComponents() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title.")
                .seriesName("Series.")
                .authors(Set.of("Author."))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}/{series}/{title}", "original.pdf");

        String[] components = result.split("/");
        for (int i = 0; i < components.length - 1; i++) { // Check directories
            assertFalse(components[i].endsWith("."), "Component " + i + " should not end with dot: " + components[i]);
        }

        assertTrue(components[0].equals("Author"));
        assertTrue(components[1].equals("Series"));
    }

    @Test
    @DisplayName("Should preserve extension for files with numeric patterns in name (e.g., Chapter 8.1.cbz)")
    void testResolvePattern_filenameWithNumericPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Comic Title")
                .seriesName("Series Name")
                .seriesNumber(8.1f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{series} - Chapter {seriesIndex}", "original.cbz");

        assertEquals("Series Name - Chapter 8.1.cbz", result, "Extension should be preserved for files with numeric patterns");
    }

    @Test
    @DisplayName("Should preserve extension for files with multiple dots in name")
    void testResolvePattern_filenameWithMultipleDots() {
        BookMetadata metadata = BookMetadata.builder()
                .title("My.Awesome.Book")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "My.Awesome.Book.epub");

        assertEquals("My.Awesome.Book.epub", result, "Extension should be preserved for files with dots in title");
    }

    @Test
    @DisplayName("Should add extension when pattern doesn't include it")
    void testResolvePattern_extensionNotInPattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author Name"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors} - {title}", "original.pdf");

        assertEquals("Author Name - Book Title.pdf", result, "Extension should be added automatically");
    }

    @Test
    @DisplayName("Should not add extension when using {currentFilename} in subdirectory")
    void testResolvePattern_currentFilenameWithPath() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "books/{currentFilename}", "My.File.With.Dots.epub");

        assertEquals("books/My.File.With.Dots.epub", result, "Extension should not be added when {currentFilename} is used, even with dots in name");
    }

    @Test
    @DisplayName("Should handle title with dots and numeric suffix without duplicating extension")
    void testResolvePattern_titleWithDotsAndNumericSuffix() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Chapter.8.1")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "Chapter.8.1.cbz");

        assertEquals("Chapter.8.1.cbz", result, "Should not treat .1 as extension");
    }

    @Test
    @DisplayName("Should preserve CBZ extension for comic files with chapter numbers")
    void testResolvePattern_comicWithChapterNumber() {
        BookMetadata metadata = BookMetadata.builder()
                .seriesName("One Punch Man")
                .seriesNumber(8.1f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{series} - Chapter {seriesIndex}", "One Punch Man - Chapter 8.1.cbz");

        assertEquals("One Punch Man - Chapter 8.1.cbz", result, "CBZ extension should be preserved for comics");
    }

    @Test
    @DisplayName("Should handle files with only numeric extension-like pattern correctly")
    void testResolvePattern_numericExtensionLikePattern() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Version 2")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}.1", "original.epub");

        assertEquals("Book Version 2.1.epub", result, "Should add real extension even when pattern ends with .1");
    }

    @Test
    @DisplayName("Should handle empty extension gracefully")
    void testResolvePattern_noExtension() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "fileWithoutExtension");

        assertEquals("Book Title", result, "Should not add extension when original file has none");
    }
}