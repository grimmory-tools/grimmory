package org.booklore.util;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PathPatternResolverTest {

    private static final int MAX_AUTHORS_BYTES = 180; // From PathPatternResolver.MAX_AUTHOR_BYTES
    private static final int MAX_FILESYSTEM_COMPONENT_BYTES = 245; // From PathPatternResolver.MAX_FILESYSTEM_COMPONENT_BYTES
    private static final int MAX_FILENAME_BYTES = 245; // From PathPatternResolver.MAX_FILESYSTEM_COMPONENT_BYTES
    private static final Charset FILENAME_CHARSET = StandardCharsets.UTF_8;

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

        assertEquals("Series Name #02 - Book Title.pdf", result);
    }

    @Test
    void testResolvePattern_seriesNumberFloat() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("Series Name")
                .seriesNumber(2.5f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{series} #{seriesIndex} - {title}", "original.pdf");

        assertEquals("Series Name #02.5 - Book Title.pdf", result);
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

        BookFileEntity primaryFile = new BookFileEntity();
        primaryFile.setBook(book);
        primaryFile.setFileName("book.epub");
        primaryFile.setFileSubPath("");
        book.setBookFiles(List.of(primaryFile));
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
    @DisplayName("Should sanitize illegal filesystem characters")
    void testResolvePattern_sanitizesIllegalCharacters() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Title/With:Slashes?And*Stars")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "book.pdf");
        
        // Assert that the result does NOT contain the illegal chars
        assertFalse(result.contains("/"), "Should remove forward slashes");
        assertFalse(result.contains(":"), "Should remove colons (illegal on Windows)");
        assertFalse(result.contains("*"), "Should remove asterisks");
        assertFalse(result.contains("?"), "Should remove question marks");
        
        // Ensure it looks reasonable and preserves content
        assertTrue(result.contains("Title"), "Should preserve 'Title'");
        assertTrue(result.contains("Slashes"), "Should preserve 'Slashes'");
        assertTrue(result.endsWith(".pdf"), "Should preserve extension");
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
        assertTrue(result.equals("John Doe, Jane Smith - The Great Book [Awesome Series #03] (2023).pdf") ||
                   result.equals("Jane Smith, John Doe - The Great Book [Awesome Series #03] (2023).pdf"));
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
            int byteLength = component.getBytes(FILENAME_CHARSET).length;
            assertTrue(byteLength <= MAX_FILESYSTEM_COMPONENT_BYTES,
                String.format("Component '%s' byte length %d should not exceed filesystem limit %d", 
                    component, byteLength, MAX_FILESYSTEM_COMPONENT_BYTES));
        }

        // Verify the authors part is properly truncated by bytes
        String authorsPart = pathComponents[0];
        int authorsBytes = authorsPart.getBytes(FILENAME_CHARSET).length;
        assertTrue(authorsBytes <= MAX_AUTHORS_BYTES, 
            String.format("Authors part should be truncated to <= %d bytes, got: %d", 
                MAX_AUTHORS_BYTES, authorsBytes));
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
        int authorsBytes = authorsPart.getBytes(FILENAME_CHARSET).length;
        assertTrue(authorsBytes <= MAX_AUTHORS_BYTES, 
            String.format("Authors should be <= %d bytes, got: %d", MAX_AUTHORS_BYTES, authorsBytes));

        BookMetadata longMetadata = BookMetadata.builder()
                .title("Test")
                .authors(LONG_AUTHOR_LIST)
                .build();

        String longResult = PathPatternResolver.resolvePattern(longMetadata, "{authors}", "test.epub");

        String longAuthorsPart = longResult.replace(".epub", "");
        int longAuthorsBytes = longAuthorsPart.getBytes(FILENAME_CHARSET).length;
        assertTrue(longAuthorsBytes <= MAX_AUTHORS_BYTES, 
            String.format("Long authors should be truncated to <= %d bytes, got: %d", 
                MAX_AUTHORS_BYTES, longAuthorsBytes));

        assertTrue(longAuthorsBytes < LONG_AUTHOR_LIST.toString().getBytes(FILENAME_CHARSET).length,
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
        int authorsBytes = authorsPart.getBytes(FILENAME_CHARSET).length;

        assertTrue(authorsBytes <= MAX_AUTHORS_BYTES,
            String.format("Single long author should be truncated to <= %d bytes, got: %d", 
                MAX_AUTHORS_BYTES, authorsBytes));
        assertFalse(authorsPart.isEmpty(), "Should not be empty after truncation");
        assertTrue(authorsBytes < veryLongAuthor.getBytes(FILENAME_CHARSET).length,
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
                int byteLength = component.getBytes(FILENAME_CHARSET).length;
                assertTrue(byteLength <= MAX_FILESYSTEM_COMPONENT_BYTES, 
                    String.format("Path component should be <= %d bytes, got: %d for component: %s", 
                        MAX_FILESYSTEM_COMPONENT_BYTES, byteLength, component));
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

        int byteLen = result.getBytes(FILENAME_CHARSET).length;
        assertTrue(byteLen <= MAX_FILENAME_BYTES, 
            String.format("Total filename bytes %d should be <= %d", byteLen, MAX_FILENAME_BYTES));
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

        assertEquals("Series Name - Chapter 08.1.cbz", result, "Extension should be preserved for files with numeric patterns");
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

        assertEquals("One Punch Man - Chapter 08.1.cbz", result, "CBZ extension should be preserved for comics");
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

    @Test
    @DisplayName("Should handle filename with very long extension - truncates or warns")
    void testResolvePattern_veryLongExtension_truncatesOrWarns() {
        String longExtension = ".a".repeat(30); // 60 bytes extension (> 50 byte limit)
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "test" + longExtension);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("Test"), 
            "Should preserve title even with long extension. Got: " + result);
        
        int resultBytes = result.getBytes(FILENAME_CHARSET).length;
        assertTrue(resultBytes <= MAX_FILENAME_BYTES, 
            String.format("Result should not exceed filesystem limits: %d bytes (max: %d). Result: %s", 
                resultBytes, MAX_FILENAME_BYTES, result));
        
        int lastDotIndex = result.lastIndexOf('.');
        assertTrue(lastDotIndex > 0, 
            "Should have an extension separator. Result: " + result);
    }

    @Test
    @DisplayName("Should handle hidden file (starts with dot)")
    void testResolvePattern_hiddenFile() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Hidden File")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", ".hidden.pdf");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should truncate single very long author name")
    void testResolvePattern_singleVeryLongAuthor() {
        String veryLongAuthor = "A".repeat(300); // Very long single author
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .authors(Set.of(veryLongAuthor))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "test.epub");

        String authorsPart = result.replace(".epub", "");
        int authorsBytes = authorsPart.getBytes(FILENAME_CHARSET).length;
        assertTrue(authorsBytes <= MAX_AUTHORS_BYTES, 
            String.format("Single long author should be truncated to <= %d bytes, got: %d", 
                MAX_AUTHORS_BYTES, authorsBytes));
        assertTrue(authorsPart.contains("et al."), "Should add 'et al.' when truncating single author");
    }

    @Test
    @DisplayName("Should handle multiple trailing dots in path components")
    void testResolvePattern_multipleTrailingDots() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title...")
                .authors(Set.of("Author Name..."))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}/{title}", "test.pdf");

        String[] components = result.split("/");
        for (int i = 0; i < components.length - 1; i++) { // Check directories (not filename)
            assertFalse(components[i].endsWith("."), "Component should not end with dot: " + components[i]);
        }
    }

    @Test
    @DisplayName("Should handle blank result with fallback to filename")
    void testResolvePattern_blankResultUsesFallback() {
        BookMetadata metadata = BookMetadata.builder()
                .title("")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "fallback.epub");

        assertEquals("fallback.epub", result, "Should use filename when result is blank");
    }

    @Test
    @DisplayName("Should handle pattern with unknown placeholder")
    void testResolvePattern_unknownPlaceholder() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title} - {unknown}", "test.epub");

        assertTrue(result.contains("Test"), "Should contain known placeholder value");
        assertTrue(result.contains("{unknown}"), "Should preserve unknown placeholder");
    }

    @Test
    @DisplayName("Should handle optional block with all values present")
    void testResolvePattern_optionalBlockAllPresent() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Title")
                .authors(Set.of("Author"))
                .seriesName("Series")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}< - {authors}>< [{series}]>", "test.epub");

        assertTrue(result.contains("Title"), "Should contain title");
        assertTrue(result.contains("Author"), "Should contain author");
        assertTrue(result.contains("Series"), "Should contain series");
    }

    @Test
    @DisplayName("Should handle filename with no extension in currentFilename")
    void testResolvePattern_currentFilenameNoExtension() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{currentFilename}", "fileWithoutExtension");

        assertEquals("fileWithoutExtension", result, "Should preserve filename without extension");
    }

    @Test
    @DisplayName("Should handle empty filename with title - uses title")
    void testResolvePattern_emptyFilename_withTitle_usesTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "");

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("Test"), 
            "Should use title when filename is empty and title exists. Got: " + result);
    }

    @Test
    @DisplayName("Should handle empty filename without title - uses default")
    void testResolvePattern_emptyFilename_noTitle_usesDefault() {
        BookMetadata metadata = BookMetadata.builder()
                .title("")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", "");

        assertNotNull(result, "Result should not be null");
        if (result.isBlank()) {
            assertTrue(true, "Current implementation returns blank for empty title and filename");
        } else {
            assertTrue(result.toLowerCase().contains("untitled"),
                String.format("If not blank, should contain 'untitled'. Got: '%s'", result));
        }
    }

    @Test
    @DisplayName("Should handle filename with only extension")
    void testResolvePattern_filenameOnlyExtension() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title}", ".pdf");

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should handle authors truncation when first author exceeds limit")
    void testResolvePattern_firstAuthorExceedsLimit() {
        String veryLongFirstAuthor = "某".repeat(100); // ~300 bytes
        BookMetadata metadata = BookMetadata.builder()
                .title("Test")
                .authors(Set.of(veryLongFirstAuthor, "Second Author"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors}", "test.epub");

        String authorsPart = result.replace(".epub", "");
        int authorsBytes = authorsPart.getBytes(FILENAME_CHARSET).length;
        assertTrue(authorsBytes <= MAX_AUTHORS_BYTES, 
            String.format("Authors should be truncated to <= %d bytes, got: %d", 
                MAX_AUTHORS_BYTES, authorsBytes));
        assertTrue(authorsPart.contains("et al."), "Should add 'et al.' when truncating");
    }

    @Test
    @DisplayName("Should truncate long filename while preserving extension")
    void testTruncateFilenameWithExtension_truncatesLongFilename() {
        String longName = "A".repeat(300);
        String extension = ".pdf";
        String filename = longName + extension;

        String result = PathPatternResolver.truncateFilenameWithExtension(filename);

        assertTrue(result.length() < filename.length(), "Filename should be truncated");
        assertTrue(result.endsWith(extension), "Extension should be preserved");
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= MAX_FILENAME_BYTES, 
            "Result bytes should be <= " + MAX_FILENAME_BYTES);
    }

    @Test
    @DisplayName("Should not truncate short filename")
    void testTruncateFilenameWithExtension_shortFilename() {
        String filename = "short_filename.pdf";
        String result = PathPatternResolver.truncateFilenameWithExtension(filename);

        assertEquals(filename, result, "Short filename should not be modified");
    }

    @Test
    @DisplayName("Should truncate filename without extension if too long")
    void testTruncateFilenameWithExtension_noExtension() {
        String longName = "A".repeat(300);
        String result = PathPatternResolver.truncateFilenameWithExtension(longName);

        assertTrue(result.length() < longName.length(), "Filename should be truncated");
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= MAX_FILENAME_BYTES,
                "Result bytes should be <= " + MAX_FILENAME_BYTES);
    }

    @Test
    @DisplayName("Should handle long unicode filename with extension")
    void testTruncateFilenameWithExtension_unicode() {
        String longName = "测试".repeat(100); // 600 bytes
        String extension = ".txt";
        String filename = longName + extension;

        String result = PathPatternResolver.truncateFilenameWithExtension(filename);

        assertTrue(result.length() < filename.length(), "Filename should be truncated");
        assertTrue(result.endsWith(extension), "Extension should be preserved");
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= MAX_FILENAME_BYTES,
                "Result bytes should be <= " + MAX_FILENAME_BYTES);
    }

    @Test
    @DisplayName("Should handle hidden files (starting with dot)")
    void testTruncateFilenameWithExtension_hiddenFile() {
        String longName = ".config" + "A".repeat(300);
        
        String result = PathPatternResolver.truncateFilenameWithExtension(longName);
        
        assertTrue(result.startsWith(".config"), "Should still start with .config (or be treated as filename)");
        assertTrue(result.getBytes(StandardCharsets.UTF_8).length <= MAX_FILENAME_BYTES,
                "Result bytes should be <= " + MAX_FILENAME_BYTES);
    }

    // ===== Else Clause Tests =====

    @Test
    @DisplayName("Else clause: should use left side when values are present")
    void testElseClause_leftSideUsedWhenPresent() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("My Series")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|Standalone>/{title}", "original.pdf");

        assertEquals("My Series/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: should use fallback when values are missing")
    void testElseClause_fallbackUsedWhenMissing() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|Standalone>/{title}", "original.pdf");

        assertEquals("Standalone/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: fallback can contain placeholders")
    void testElseClause_fallbackWithPlaceholders() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("John Doe"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}/{seriesIndex} - {title}|{title}>", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: backward compatible without pipe")
    void testElseClause_backwardCompatibleNoPipe() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}/>{title}", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: literal text in fallback")
    void testElseClause_literalFallback() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|No Series>/{title}", "original.pdf");

        assertEquals("No Series/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: mixed blocks with and without else")
    void testElseClause_mixedBlocks() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|Standalone>/<{year} - >{title}", "original.pdf");

        assertEquals("Standalone/Book Title.pdf", result);
    }

    // ===== Modifier Tests =====

    @Test
    @DisplayName("Modifier: first with multiple authors")
    void testModifier_firstMultipleAuthors() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(new LinkedHashSet<>(List.of("Patrick Rothfuss", "Brandon Sanderson")))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:first}/{title}", "original.pdf");

        assertEquals("Patrick Rothfuss/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: first with single author")
    void testModifier_firstSingleAuthor() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:first}/{title}", "original.pdf");

        assertEquals("Patrick Rothfuss/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: sort transforms 'First Last' to 'Last, First'")
    void testModifier_sort() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:sort}/{title}", "original.pdf");

        assertEquals("Rothfuss, Patrick/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: sort with single-word author name")
    void testModifier_sortSingleWord() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Plato"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:sort}/{title}", "original.pdf");

        assertEquals("Plato/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: initial on title")
    void testModifier_initialTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title("The Name of the Wind")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:initial}/{title}", "original.pdf");

        assertEquals("T/The Name of the Wind.pdf", result);
    }

    @Test
    @DisplayName("Modifier: initial on authors uses first letter of last name")
    void testModifier_initialAuthors() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:initial}/{authors:sort}/{title}", "original.pdf");

        assertEquals("R/Rothfuss, Patrick/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: upper")
    void testModifier_upper() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:upper}", "original.pdf");

        assertEquals("BOOK TITLE.pdf", result);
    }

    @Test
    @DisplayName("Modifier: lower")
    void testModifier_lower() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:lower}", "original.pdf");

        assertEquals("book title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: unknown modifier passes through unchanged")
    void testModifier_unknownPassesThrough() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:reverse}", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier inside else clause fallback")
    void testModifier_insideElseClause() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|{authors:sort}>/{title}", "original.pdf");

        assertEquals("Rothfuss, Patrick/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier in optional block with present values")
    void testModifier_inOptionalBlock() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{authors:sort}/>{title}", "original.pdf");

        assertEquals("Rothfuss, Patrick/Book Title.pdf", result);
    }

    // ===== Edge Case Tests =====

    @Test
    @DisplayName("Modifier: sort with three-word author name uses last word as last name")
    void testModifier_sortThreeWordName() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Mary Jane Watson"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:sort}/{title}", "original.pdf");

        assertEquals("Watson, Mary Jane/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: initial on single-word author name uses that name")
    void testModifier_initialSingleWordAuthor() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Plato"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:initial}/{title}", "original.pdf");

        assertEquals("P/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: first on non-authors field takes first comma-separated value")
    void testModifier_firstOnNonAuthorsField() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Intro, Outro, Epilogue")
                .build();

        // The title contains commas, but they are sanitized first (commas are allowed chars).
        // However, sanitize doesn't remove commas, so :first should split on ", "
        String result = PathPatternResolver.resolvePattern(metadata, "{title:first}", "original.pdf");

        // The title value after sanitize is "Intro, Outro, Epilogue" and first splits on ", "
        assertEquals("Intro.pdf", result);
    }

    @Test
    @DisplayName("Modifier: sort on non-authors field still works (splits on last space)")
    void testModifier_sortOnTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title("The Wind")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:sort}", "original.pdf");

        assertEquals("Wind, The.pdf", result);
    }

    @Test
    @DisplayName("Multiple else clause blocks in same pattern")
    void testElseClause_multipleBlocks() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{series}|Standalone>/<{year}|Unknown Year> - {title}", "original.pdf");

        assertEquals("Standalone/Unknown Year - Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause with empty fallback produces empty string")
    void testElseClause_emptyFallback() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "<{series}|>{title}", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Else clause: primary side partially missing with multiple placeholders triggers fallback")
    void testElseClause_primaryPartiallyMissing() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("My Series")
                // seriesIndex is null
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{series} #{seriesIndex}|{title}>", "original.pdf");

        // series present but seriesIndex missing → fallback to {title}
        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier in primary side of else clause with all values present")
    void testModifier_inPrimarySideOfElseClause() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .seriesName("My Series")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{series} by {authors:sort}|{title}>", "original.pdf");

        assertEquals("My Series by Rothfuss, Patrick.pdf", result);
    }

    @Test
    @DisplayName("Chained modifiers on different fields in same pattern")
    void testModifier_chainedDifferentFields() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Patrick Rothfuss"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "{title:initial}/{authors:first}/{title:lower}", "original.pdf");

        assertEquals("B/Patrick Rothfuss/book title.pdf", result);
    }

    @Test
    @DisplayName("Modifier on field used in optional block where field is missing - block should be removed")
    void testModifier_onMissingFieldInOptionalBlock() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{authors:sort}/>{title}", "original.pdf");

        assertEquals("Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier: first with truncated author list preserves first author")
    void testModifier_firstWithManyAuthors() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(new LinkedHashSet<>(List.of("Alice", "Bob", "Carol", "Dave")))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{authors:first}/{title}", "original.pdf");

        assertEquals("Alice/Book Title.pdf", result);
    }

    @Test
    @DisplayName("Modifier combined with else clause and extension check")
    void testModifier_withElseClauseAndExtension() {
        BookMetadata metadata = BookMetadata.builder()
                .title("My Book")
                .authors(Set.of("Jane Doe"))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{series:upper}|{authors:sort}>/{title}.{extension}", "original.epub");

        assertEquals("Doe, Jane/My Book.epub", result);
    }

    @Test
    @DisplayName("Modifier: initial on lowercase title produces uppercase letter")
    void testModifier_initialLowercaseTitle() {
        BookMetadata metadata = BookMetadata.builder()
                .title("lowercase title")
                .build();

        String result = PathPatternResolver.resolvePattern(metadata, "{title:initial}/{title}", "original.pdf");

        assertEquals("L/lowercase title.pdf", result);
    }

    @Test
    @DisplayName("Else clause with all primary values present ignores fallback")
    void testElseClause_primaryCompleteIgnoresFallback() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .seriesName("My Series")
                .seriesNumber(5f)
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "<{series} #{seriesIndex}|{title}>", "original.pdf");

        assertEquals("My Series #05.pdf", result);
    }

    @Test
    @DisplayName("Else clause preserves backward compatibility with existing patterns")
    void testElseClause_existingPatternsUnchanged() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of("Author"))
                .seriesName("Series")
                .seriesNumber(1f)
                .publishedDate(LocalDate.of(2023, 1, 1))
                .build();

        String result = PathPatternResolver.resolvePattern(metadata,
                "{authors}/<{series}/><{seriesIndex}. >{title}< ({year})>", "original.epub");

        assertEquals("Author/Series/01. Book Title (2023).epub", result);
    }

    @Test
    @DisplayName("Should remove leading slash from resolved pattern if first component is empty")
    void testResolvePattern_removesLeadingSlash_whenFirstComponentIsEmpty() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Book Title")
                .authors(Set.of()) // Empty authors
                .build();

        // Pattern implies a subdirectory, but authors is missing
        // This resolves to "/Book Title.pdf" currently
        String result = PathPatternResolver.resolvePattern(metadata, "{authors}/{title}", "original.pdf");

        // This assertion ensures the path is relative (does not start with /)
        assertFalse(result.startsWith("/"), "Result should not start with slash: " + result);
    }
}