package org.booklore;

import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.util.PathPatternResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PathPatternResolverTest {

    private BookEntity createBook(String title, String subtitle, List<String> authors, LocalDate date,
                                  String series, Float seriesNum, String lang,
                                  String publisher, String isbn13, String isbn10,
                                  String fileName) {
        BookEntity book = mock(BookEntity.class);
        BookFileEntity primaryFile = mock(BookFileEntity.class);
        BookMetadataEntity metadata = mock(BookMetadataEntity.class);

        when(book.getMetadata()).thenReturn(metadata);
        when(book.getPrimaryBookFile()).thenReturn(primaryFile);
        when(primaryFile.getFileName()).thenReturn(fileName);

        when(metadata.getTitle()).thenReturn(title);
        when(metadata.getSubtitle()).thenReturn(subtitle);

        if (authors == null) {
            when(metadata.getAuthors()).thenReturn(null);
        } else {
            AtomicLong idCounter = new AtomicLong(1);
            LinkedHashSet<AuthorEntity> authorEntities = authors.stream().map(name -> {
                AuthorEntity a = new AuthorEntity();
                a.setId(idCounter.getAndIncrement());
                a.setName(name);
                return a;
            }).collect(Collectors.toCollection(LinkedHashSet::new));
            when(metadata.getAuthors()).thenReturn(authorEntities);
        }

        when(metadata.getPublishedDate()).thenReturn(date);
        when(metadata.getSeriesName()).thenReturn(series);
        when(metadata.getSeriesNumber()).thenReturn(seriesNum);
        when(metadata.getLanguage()).thenReturn(lang);
        when(metadata.getPublisher()).thenReturn(publisher);
        when(metadata.getIsbn13()).thenReturn(isbn13);
        when(metadata.getIsbn10()).thenReturn(isbn10);

        return book;
    }

    // Helper method for backward compatibility
    private BookEntity createBook(String title, List<String> authors, LocalDate date,
                                  String series, Float seriesNum, String lang,
                                  String publisher, String isbn13, String isbn10,
                                  String fileName) {
        return createBook(title, null, authors, date, series, seriesNum, lang, publisher, isbn13, isbn10, fileName);
    }

    @Test void emptyPattern_returnsOnlyExtension() {
        var book = createBook("Title", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, "")).isEqualTo("file.pdf");
    }

    @Test void optionalBlockMissingPlaceholder_removed() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}/>")).isEqualTo("f.epub");
    }

    @Test void multipleOptionalBlocks_partialValues() {
        var book = createBook("Title", List.of("Author"), LocalDate.of(2020, 1, 1),
                "Series", 1f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. ><{language}/>{title}";
        assertThat(PathPatternResolver.resolvePattern(book, p)).isEqualTo("Series/01. Title.epub");
    }

    @Test void placeholdersOutsideOptional_replacedWithEmpty() {
        var book = createBook("Title", null, LocalDate.of(2020, 1, 1), null, null, null, null, null, null, "file.cbz");
        String p = "{title} - {authors} - {publisher}";
        assertThat(PathPatternResolver.resolvePattern(book, p)).isEqualTo("Title -  - .cbz");
    }

    @Test void seriesNumber_decimalAndInteger() {
        var book1 = createBook("Title", List.of("Author"), LocalDate.now(), "Series", 3.5f, null, null, null, null, "f.epub");
        var book2 = createBook("Title", List.of("Author"), LocalDate.now(), "Series", 3f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. >{title}";
        assertThat(PathPatternResolver.resolvePattern(book1, p)).isEqualTo("Series/03.5. Title.epub");
        assertThat(PathPatternResolver.resolvePattern(book2, p)).isEqualTo("Series/03. Title.epub");
    }

    @Test void sanitizes_illegalCharsAndWhitespace() {
        var book = createBook(" Ti:tle/<>|*? ", List.of("Au:thor|?*"), LocalDate.of(2000, 1, 1),
                "Se:ries", 1f, "Lang<>", "Pub|?", "123:456", "654|321", "file?.pdf");
        String p = "{title} - {authors} - {series} - {language} - {publisher} - {isbn}";
        String result = PathPatternResolver.resolvePattern(book, p);
        assertThat(result).doesNotContain(":", "/", "*", "?", "<", ">", "|")
                .contains("Title").contains("Author").contains("Series");
    }

    @Test void noExtension_returnsNoDot() {
        var book = createBook("Title", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "filenameWithoutExt");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}")).isEqualTo("Title");
    }

    @Test void complexPattern_allPlaceholdersPresent() {
        var book = createBook("Complex Title", List.of("Author One"), LocalDate.of(2010, 5, 5),
                "Complex Series", 12.5f, "English", "Publisher", "ISBN13", "ISBN10", "complex.cbz");
        String p = "<{series}/><{seriesIndex}. ><{language}/><{publisher}/>{title} - {authors} - {year} - {isbn}";
        assertThat(PathPatternResolver.resolvePattern(book, p))
                .isEqualTo("Complex Series/12.5. English/Publisher/Complex Title - Author One - 2010 - ISBN13.cbz");
    }

    @Test void missingAllMetadata_returnsExtensionOnly() {
        var b = createBook(null, null, null, null, null, null, null, null, null, "f.pdf");
        assertThat(PathPatternResolver.resolvePattern(b, "")).isEqualTo("f.pdf");
    }

    @Test void optionalBlockWithEmptyValue_removesBlock() {
        var b = createBook(null, null, null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(b, "<{series}/><{language}/>")).isEqualTo("f.epub");
    }

    @Test void placeholderWithEmptyValue_outsideOptional_replacedByEmpty() {
        var b = createBook("T", null, null, null, null, null, null, null, null, "f.epub");
        String p = "{title} - {authors} - {language}";
        assertThat(PathPatternResolver.resolvePattern(b, p)).isEqualTo("T -  - .epub");
    }

    @Test void complexPattern_someMissingOptionalBlocksSkipped() {
        var b = createBook("T", List.of("A"), null, null, null, "en", null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. ><{language}/>{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(b, p)).isEqualTo("en/T - A.epub");
    }

    @Test void seriesNumberDecimals_andIntegersHandledProperly() {
        var b1 = createBook("T", List.of("A"), null, "S", 1f, null, null, null, null, "f.epub");
        var b2 = createBook("T", List.of("A"), null, "S", 1.5f, null, null, null, null, "f.epub");
        String p = "<{series}/><{seriesIndex}. >{title}";
        assertThat(PathPatternResolver.resolvePattern(b1, p)).isEqualTo("S/01. T.epub");
        assertThat(PathPatternResolver.resolvePattern(b2, p)).isEqualTo("S/01.5. T.epub");
    }

    @Test void sanitize_removesIllegalCharacters() {
        var b = createBook("Ti:tle<>", List.of("Au:thor|?*"), null, "Se:ries", 1f,
                "La<>ng", "Pub|?", "123:456", "654|321", "f?.pdf");
        String p = "{title}-{authors}-{series}-{language}-{publisher}-{isbn}";
        String result = PathPatternResolver.resolvePattern(b, p);
        assertThat(result).doesNotContain(":", "/", "*", "?", "<", ">", "|")
                .contains("Title").contains("Author").contains("Series");
    }

    @Test void noFileExtension_returnsNoDot() {
        var b = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "fileNoExt");
        assertThat(PathPatternResolver.resolvePattern(b, "{title}")).isEqualTo("Title");
    }

    @Test void patternWithOnlyExtension_returnsExtensionOnly() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "sample.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, ".{extension}")).isEqualTo(".pdf");
    }

    @Test void patternWithExtensionAndFilenamePlaceholder_works() {
        var book = createBook("Sample", null, null, null, null, null, null, null, null, "original.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{currentFilename}")).isEqualTo("original.epub");
    }

    @Test void allPlaceholdersMissing_yieldsJustExtension() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "file.cbz");
        String pattern = "{title}-{authors}-{series}-{year}-{language}-{publisher}-{isbn}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("file------.cbz");
    }

    @Test void patternWithBackslashes_isSanitized() {
        var book = createBook("Ti\\tle", List.of("Au\\thor"), null, null, null, null, null, null, null, "f.pdf");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}/{authors}")).isEqualTo("Title/Author.pdf");
    }

    @Test void optionalBlockWithMixedResolvedAndEmptyValues_skippedEntirely() {
        var book = createBook("Title", null, null, null, null, null, null, null, null, "file.epub");
        String pattern = "<{authors} - {series}/>{title}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("Title.epub");
    }

    @Test void placeholderWithWhitespace_trimmedAndSanitized() {
        var book = createBook("   My  Book  ", List.of("  John   Doe "), null, null, null, null, null, null, null, "book.pdf");
        String pattern = "{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("My Book - John Doe.pdf");
    }

    @Test void multipleAuthors_concatenatedProperly() {
        var book = createBook("Book", List.of("Alice", "Bob", "Carol"), null, null, null, null, null, null, null, "book.pdf");
        String pattern = "{title} - {authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("Book - Alice, Bob, Carol.pdf");
    }

    @Test void patternWithOnlyOptional_allEmpty_resolvesToFilename() {
        var book = createBook(null, null, null, null, null, null, null, null, null, "fallback.epub");
        String pattern = "<{series}/><{language}/>";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("fallback.epub");
    }

    @Test void invalidFilename_noExtension_stillResolves() {
        var book = createBook("A", List.of("B"), null, null, null, null, null, null, null, "noExt");
        String pattern = "{title}-{authors}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("A-B");
    }

    @Test void patternEndsWithSlash_slashIsPreserved() {
        var book = createBook("T", List.of("A"), null, "S", 1f, null, null, null, null, "b.pdf");
        String pattern = "<{series}/><{seriesIndex}/>{title}/";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("S/01/T/.pdf");
    }

    @Test void extensionDotEscaped_doubleDotsNotAdded() {
        var book = createBook("X", List.of("Y"), null, null, null, null, null, null, null, "book.mobi");
        String pattern = "{title}.{extension}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern)).isEqualTo("X.mobi");
    }

    @Test void subtitleInPattern_replacedCorrectly() {
        var book = createBook("Main Title", "The Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Main Title - The Subtitle.epub");
    }

    @Test void subtitleEmpty_replacedWithEmpty() {
        var book = createBook("Title", "", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - .epub");
    }

    @Test void subtitleNull_replacedWithEmpty() {
        var book = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - .epub");
    }

    @Test void subtitleInOptionalBlock_withValue_blockIncluded() {
        var book = createBook("Title", "Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}< - {subtitle}>")).isEqualTo("Title - Subtitle.epub");
    }

    @Test void subtitleInOptionalBlock_withoutValue_blockRemoved() {
        var book = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title}< - {subtitle}>")).isEqualTo("Title.epub");
    }

    @Test void subtitleWithIllegalChars_sanitized() {
        var book = createBook("Title", "Sub:title<>|*?", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        String result = PathPatternResolver.resolvePattern(book, "{title} - {subtitle}");
        assertThat(result).doesNotContain(":", "<", ">", "|", "*", "?")
                .contains("Title").contains("Subtitle");
    }

    @Test void subtitleWithWhitespace_trimmedAndSanitized() {
        var book = createBook("Title", "   Sub  title  ", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title} - {subtitle}")).isEqualTo("Title - Sub title.epub");
    }

    @Test void complexPatternWithSubtitle_allPlaceholdersPresent() {
        var book = createBook("Main Title", "The Great Subtitle", List.of("Author One"), LocalDate.of(2010, 5, 5),
                "Series", 1f, "English", "Publisher", "ISBN13", "ISBN10", "complex.epub");
        String pattern = "<{series}/>{title}< - {subtitle}> - {authors} - {year}";
        assertThat(PathPatternResolver.resolvePattern(book, pattern))
                .isEqualTo("Series/Main Title - The Great Subtitle - Author One - 2010.epub");
    }

    @Test void optionalBlockWithTitleAndSubtitle_partialValues() {
        var book1 = createBook("Title", "Subtitle", List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        var book2 = createBook("Title", null, List.of("Author"), LocalDate.now(), null, null, null, null, null, null, "file.epub");
        String pattern = "<{title} - {subtitle}>";

        assertThat(PathPatternResolver.resolvePattern(book1, pattern)).isEqualTo("Title - Subtitle.epub");
        assertThat(PathPatternResolver.resolvePattern(book2, pattern)).isEqualTo("file.epub");
    }

    // ===== Else Clause Tests =====

    @Test void elseClause_leftSideWhenPresent() {
        var book = createBook("Title", List.of("Author"), null, "Series", 1f, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|Standalone>/{title}")).isEqualTo("Series/Title.epub");
    }

    @Test void elseClause_fallbackWhenMissing() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|Standalone>/{title}")).isEqualTo("Standalone/Title.epub");
    }

    @Test void elseClause_fallbackWithPlaceholders() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}/{seriesIndex} - {title}|{title}>")).isEqualTo("Title.epub");
    }

    @Test void elseClause_backwardCompatibleNoPipe() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}/>{title}")).isEqualTo("Title.epub");
    }

    @Test void elseClause_mixedBlocksWithAndWithoutElse() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|Standalone>/<{year} - >{title}")).isEqualTo("Standalone/Title.epub");
    }

    // ===== Modifier Tests =====

    @Test void modifier_firstMultipleAuthors() {
        var book = createBook("Title", List.of("Patrick Rothfuss", "Brandon Sanderson"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:first}/{title}")).isEqualTo("Patrick Rothfuss/Title.epub");
    }

    @Test void modifier_sortAuthor() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:sort}/{title}")).isEqualTo("Rothfuss, Patrick/Title.epub");
    }

    @Test void modifier_initialTitle() {
        var book = createBook("The Name of the Wind", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title:initial}/{title}")).isEqualTo("T/The Name of the Wind.epub");
    }

    @Test void modifier_initialAuthorsUsesLastName() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:initial}/{authors:sort}/{title}")).isEqualTo("R/Rothfuss, Patrick/Title.epub");
    }

    @Test void modifier_upper() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title:upper}")).isEqualTo("TITLE.epub");
    }

    @Test void modifier_lower() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title:lower}")).isEqualTo("title.epub");
    }

    @Test void modifier_insideElseClause() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|{authors:sort}>/{title}")).isEqualTo("Rothfuss, Patrick/Title.epub");
    }

    @Test void modifier_inOptionalBlock() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{authors:sort}/>{title}")).isEqualTo("Rothfuss, Patrick/Title.epub");
    }

    // ===== Edge Case Tests =====

    @Test void modifier_sortThreeWordName() {
        var book = createBook("T", List.of("Mary Jane Watson"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:sort}/{title}")).isEqualTo("Watson, Mary Jane/T.epub");
    }

    @Test void modifier_initialSingleWordAuthor() {
        var book = createBook("T", List.of("Plato"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:initial}/{title}")).isEqualTo("P/T.epub");
    }

    @Test void elseClause_multipleBlocks() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|Standalone>/<{year}|Unknown> - {title}"))
                .isEqualTo("Standalone/Unknown - Title.epub");
    }

    @Test void elseClause_emptyFallback() {
        var book = createBook("Title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series}|>{title}")).isEqualTo("Title.epub");
    }

    @Test void elseClause_primaryPartiallyMissing() {
        var book = createBook("Title", List.of("Author"), null, "Series", null, null, null, null, null, "f.epub");
        // series present but seriesIndex missing → fallback
        assertThat(PathPatternResolver.resolvePattern(book, "<{series} #{seriesIndex}|{title}>")).isEqualTo("Title.epub");
    }

    @Test void modifier_inPrimarySideOfElseClause() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, "Series", null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series} by {authors:sort}|{title}>"))
                .isEqualTo("Series by Rothfuss, Patrick.epub");
    }

    @Test void modifier_chainedDifferentFields() {
        var book = createBook("Title", List.of("Patrick Rothfuss"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title:initial}/{authors:first}/{title:lower}"))
                .isEqualTo("T/Patrick Rothfuss/title.epub");
    }

    @Test void modifier_onMissingFieldInOptionalBlock() {
        var book = createBook("Title", null, null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{authors:sort}/>{title}")).isEqualTo("Title.epub");
    }

    @Test void modifier_firstWithManyAuthors() {
        var book = createBook("Title", List.of("Alice", "Bob", "Carol", "Dave"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors:first}/{title}")).isEqualTo("Alice/Title.epub");
    }

    @Test void modifier_withElseClauseAndExtensionPlaceholder() {
        var book = createBook("Title", List.of("Jane Doe"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series:upper}|{authors:sort}>/{title}.{extension}"))
                .isEqualTo("Doe, Jane/Title.epub");
    }

    @Test void modifier_initialLowercaseTitle() {
        var book = createBook("lowercase title", List.of("Author"), null, null, null, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{title:initial}/{title}")).isEqualTo("L/lowercase title.epub");
    }

    @Test void elseClause_primaryCompleteIgnoresFallback() {
        var book = createBook("Title", List.of("Author"), null, "Series", 5f, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "<{series} #{seriesIndex}|{title}>")).isEqualTo("Series #05.epub");
    }

    @Test void elseClause_existingPatternsUnchanged() {
        var book = createBook("Title", List.of("Author"), LocalDate.of(2023, 1, 1), "Series", 1f, null, null, null, null, "f.epub");
        assertThat(PathPatternResolver.resolvePattern(book, "{authors}/<{series}/><{seriesIndex}. >{title}< ({year})>"))
                .isEqualTo("Author/Series/01. Title (2023).epub");
    }
}