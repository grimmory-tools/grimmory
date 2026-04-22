package org.booklore.util;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookFileGroupingUtilsTest {

    @Test
    void extractGroupingKey_shouldStripExtension() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit.epub")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldStripFormatIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (PDF).pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (EPUB).epub")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [pdf].pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [EPUB].epub")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldBeCaseInsensitive() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("THE HOBBIT.pdf"))
                .isEqualTo(BookFileGroupingUtils.extractGroupingKey("the hobbit.epub"));
    }

    @Test
    void extractGroupingKey_shouldNormalizeWhitespace() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The   Hobbit.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("  The Hobbit  .pdf")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldHandleNullAndEmpty() {
        assertThat(BookFileGroupingUtils.extractGroupingKey(null)).isEqualTo("");
        assertThat(BookFileGroupingUtils.extractGroupingKey("")).isEqualTo("");
    }

    @Test
    void extractGroupingKey_shouldHandleFileWithNoExtension() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("noextension")).isEqualTo("noextension");
    }

    @Test
    void extractGroupingKey_shouldNotStripDotsThatAreNotExtensions() {
        // Folder names with dots (like "1. American Gods") should not be stripped
        // The dot after "1" is NOT a file extension
        assertThat(BookFileGroupingUtils.extractGroupingKey("1. American Gods - Neil Gaiman (2011)"))
                .isEqualTo("1. american gods - neil gaiman (2011)");
        // Series numbering with dots
        assertThat(BookFileGroupingUtils.extractGroupingKey("Vol. 1 - Adventures (2020)"))
                .isEqualTo("vol. 1 - adventures (2020)");
        // Title with dots
        assertThat(BookFileGroupingUtils.extractGroupingKey("Dr. Seuss Collection"))
                .isEqualTo("dr. seuss collection");
        // Multiple dots that are not extensions
        assertThat(BookFileGroupingUtils.extractGroupingKey("U.S.A. History"))
                .isEqualTo("u.s.a. history");
    }

    @Test
    void extractGroupingKey_shouldHandleMultipleFormatIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("Book (PDF) (EPUB).pdf")).isEqualTo("book");
    }

    @Test
    void extractGroupingKey_shouldNotStripNonFormatParentheses() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("Book (Extended Edition).pdf")).isEqualTo("book (extended edition)");
    }

    @Test
    void generateDirectoryGroupKey_shouldCombineSubPathAndGroupingKey() {
        String key = BookFileGroupingUtils.generateDirectoryGroupKey("fantasy/tolkien", "The Hobbit.pdf");
        assertThat(key).isEqualTo("fantasy/tolkien:the hobbit");
    }

    @Test
    void generateDirectoryGroupKey_shouldHandleNullSubPath() {
        String key = BookFileGroupingUtils.generateDirectoryGroupKey(null, "The Hobbit.pdf");
        assertThat(key).isEqualTo(":the hobbit");
    }

    @Test
    void groupByBaseName_shouldGroupFilesWithSameBaseNameInSameDirectory() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, pdf));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, pdf);
    }

    @Test
    void groupByBaseName_shouldSeparateFilesInDifferentDirectories() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("dir1")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("dir2")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldSeparateFilesFromDifferentLibraryPaths() {
        LibraryPathEntity path1 = new LibraryPathEntity();
        path1.setId(1L);
        path1.setPath("/library1");

        LibraryPathEntity path2 = new LibraryPathEntity();
        path2.setId(2L);
        path2.setPath("/library2");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(path1)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(path2)
                .fileSubPath("books")
                .fileName("book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldGroupFormatIndicatorVariants() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("books")
                .fileName("The Hobbit (PDF).pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, pdf));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, pdf);
    }

    @Test
    void extractGroupingKey_shouldConvertUnderscoresToSpaces() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The_Hobbit.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Lord_of_the_Rings.epub")).isEqualTo("lord of the rings");
    }

    @Test
    void extractGroupingKey_shouldStripTrailingAuthor() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit - J.R.R. Tolkien.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("1984 - George Orwell.epub")).isEqualTo("1984");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Pride and Prejudice - Jane Austen.pdf")).isEqualTo("pride and prejudice");
    }

    @Test
    void extractGroupingKey_shouldRepositionArticles() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("Hobbit, The.pdf")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Tale of Two Cities, A.epub")).isEqualTo("a tale of two cities");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Unexpected Journey, An.pdf")).isEqualTo("an unexpected journey");
    }

    @Test
    void extractGroupingKey_shouldHandleCombinedNormalizations() {
        // Underscores + author
        assertThat(BookFileGroupingUtils.extractGroupingKey("The_Hobbit - J.R.R. Tolkien.pdf")).isEqualTo("the hobbit");
        // Article + author
        assertThat(BookFileGroupingUtils.extractGroupingKey("Hobbit, The - J.R.R. Tolkien.pdf")).isEqualTo("the hobbit");
        // Underscores + article
        assertThat(BookFileGroupingUtils.extractGroupingKey("Hobbit,_The.pdf")).isEqualTo("the hobbit");
    }

    @Test
    void calculateSimilarity_shouldReturnHighScoreForSimilarStrings() {
        double similarity = BookFileGroupingUtils.calculateSimilarity("the hobbit", "the hobit");
        assertThat(similarity).isGreaterThan(0.8);
    }

    @Test
    void calculateSimilarity_shouldReturnPerfectScoreForIdenticalStrings() {
        double similarity = BookFileGroupingUtils.calculateSimilarity("the hobbit", "the hobbit");
        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    void calculateSimilarity_shouldReturnLowScoreForDifferentStrings() {
        double similarity = BookFileGroupingUtils.calculateSimilarity("the hobbit", "1984");
        assertThat(similarity).isLessThan(0.5);
    }

    @Test
    void calculateSimilarity_shouldHandleNullAndEmptyStrings() {
        assertThat(BookFileGroupingUtils.calculateSimilarity(null, "test")).isEqualTo(0);
        assertThat(BookFileGroupingUtils.calculateSimilarity("test", null)).isEqualTo(0);
        assertThat(BookFileGroupingUtils.calculateSimilarity("", "test")).isEqualTo(0);
        assertThat(BookFileGroupingUtils.calculateSimilarity("test", "")).isEqualTo(0);
    }

    // ========== Folder-Centric Grouping Tests ==========

    @Test
    void groupByBaseName_shouldGroupExtendedNamesMatchingFolderName() {
        // The "American Gods" case - files with extended names should group with folder
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile mobi = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods.mobi")
                .bookFileType(BookFileType.MOBI)
                .build();

        LibraryFile m4b = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods The Tenth Anniversary Edition (A Full Cast Production).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, mobi, m4b));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, mobi, m4b);
    }

    @Test
    void groupByBaseName_shouldGroupEditionVariantsWithFolderName() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile regular = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Hobbit")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile anniversary = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Hobbit")
                .fileName("The Hobbit 50th Anniversary Edition.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        LibraryFile unabridged = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Hobbit")
                .fileName("The Hobbit Unabridged.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(regular, anniversary, unabridged));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(regular, anniversary, unabridged);
    }

    @Test
    void groupByBaseName_shouldKeepSeriesEntriesSeparate() {
        // Harry Potter series should remain as separate books
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile book1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 1.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile book2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 2.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile book3 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 3.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(book1, book2, book3));

        assertThat(groups).hasSize(3);
    }

    @Test
    void groupByBaseName_shouldKeepSeriesWithNumbersSeparate() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile vol1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Lord of the Rings")
                .fileName("Lord of the Rings Vol 1.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile vol2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Lord of the Rings")
                .fileName("Lord of the Rings Vol 2.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(vol1, vol2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldGroupSeriesFormatsWithSameNumber() {
        // Same series entry in different formats should group
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile book1epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 1.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile book1pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 1.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        LibraryFile book2epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Book 2.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(book1epub, book1pdf, book2epub));

        assertThat(groups).hasSize(2);
        // Book 1 should have 2 files
        boolean foundBook1Group = groups.values().stream()
                .anyMatch(list -> list.size() == 2 && list.containsAll(List.of(book1epub, book1pdf)));
        assertThat(foundBook1Group).isTrue();
    }

    @Test
    void groupByBaseName_shouldSeparateUnrelatedBooksInAuthorFolder() {
        // Multiple different books by same author should stay separate
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile americanGods = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman")
                .fileName("American Gods.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile coraline = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman")
                .fileName("Coraline.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile stardust = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman")
                .fileName("Stardust.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(americanGods, coraline, stardust));

        assertThat(groups).hasSize(3);
    }

    @Test
    void groupByBaseName_shouldUseExactMatchingForRootLevelFiles() {
        // Files at root level (no subfolder) should use exact key matching
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("The Hobbit.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        LibraryFile other = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("1984.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, pdf, other));

        assertThat(groups).hasSize(2);
        // Hobbit files should be grouped
        boolean foundHobbitGroup = groups.values().stream()
                .anyMatch(list -> list.size() == 2 && list.containsAll(List.of(epub, pdf)));
        assertThat(foundHobbitGroup).isTrue();
    }

    @Test
    void groupByBaseName_shouldGroupFilesWithAuthorVariationsMatchingFolder() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile basic = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile withAuthor = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods - Neil Gaiman.mobi")
                .bookFileType(BookFileType.MOBI)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(basic, withAuthor));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(basic, withAuthor);
    }

    // ========== Part Indicator Stripping Tests ==========

    @Test
    void extractGroupingKey_shouldStripPartIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Lost Hero (part 1).m4b")).isEqualTo("the lost hero");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Lost Hero (pt 2).m4b")).isEqualTo("the lost hero");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Lost Hero (disc 1).m4b")).isEqualTo("the lost hero");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Lost Hero [cd 3].m4b")).isEqualTo("the lost hero");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Lost Hero - part 1.m4b")).isEqualTo("the lost hero");
    }

    @Test
    void extractGroupingKey_shouldPreserveNonPartParenthetical() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (Extended Edition).epub"))
                .isEqualTo("the hobbit (extended edition)");
    }

    @Test
    void extractGroupingKey_shouldProduceSameKeyForMultiPartFiles() {
        String key1 = BookFileGroupingUtils.extractGroupingKey("1. The Lost Hero (part 1).m4b");
        String key2 = BookFileGroupingUtils.extractGroupingKey("1. The Lost Hero (part 2).m4b");
        assertThat(key1).isEqualTo(key2);
    }

    // ========== Bare-Number Series Detection Tests ==========

    @Test
    void extractGroupingKey_shouldNotStripBarePartAsSeriesLabel() {
        // "Part 1" without brackets/dash is a series label, not a multi-file indicator
        assertThat(BookFileGroupingUtils.extractGroupingKey("Harry Potter Part 1.epub"))
                .isEqualTo("harry potter part 1");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Harry Potter Part 2.epub"))
                .isEqualTo("harry potter part 2");
    }

    @Test
    void groupByBaseName_shouldKeepBarePartSeriesEntriesSeparate() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile part1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Part 1.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile part2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Harry Potter")
                .fileName("Harry Potter Part 2.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(part1, part2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldSeparateBareNumberedSeriesFiles() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Percy Jackson")
                .fileName("1. The Lightning Thief.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Percy Jackson")
                .fileName("2. The Sea of Monsters.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldGroupMultiPartFilesOfSameBook() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile part1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus")
                .fileName("1. The Lost Hero (part 1).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile part2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus")
                .fileName("1. The Lost Hero (part 2).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(part1, part2));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(part1, part2);
    }

    @Test
    void groupByBaseName_shouldHandleMixedPartsAndSeparateBooks() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile lostHeroPt1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus")
                .fileName("1. The Lost Hero (part 1).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile lostHeroPt2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus")
                .fileName("1. The Lost Hero (part 2).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile sonOfNeptune = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus")
                .fileName("2. The Son of Neptune.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(lostHeroPt1, lostHeroPt2, sonOfNeptune));

        assertThat(groups).hasSize(2);
        boolean foundLostHeroGroup = groups.values().stream()
                .anyMatch(list -> list.size() == 2 && list.containsAll(List.of(lostHeroPt1, lostHeroPt2)));
        assertThat(foundLostHeroGroup).isTrue();
    }

    @Test
    void groupByBaseName_shouldHandleFolderBasedAudiobook() {
        // Folder-based audiobook (MP3 folder) with ebook files
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods.epub")
                .bookFileType(BookFileType.EPUB)
                .folderBased(false)
                .build();

        // Folder-based audiobook appears as "American Gods" subfolder
        LibraryFile mp3Folder = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("American Gods")
                .fileName("American Gods")
                .bookFileType(BookFileType.AUDIOBOOK)
                .folderBased(true)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, mp3Folder));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, mp3Folder);
    }

    @Test
    void groupByBaseName_shouldHandleNestedFolderPath() {
        // Files in nested folder like "Neil Gaiman/American Gods"
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("American Gods.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile m4b = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("American Gods 10th Anniversary.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, m4b));

        // Should use last folder component "American Gods" as reference
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, m4b);
    }

    @Test
    void groupByBaseName_shouldGroupNestedAudiobookFolderWithEbooks() {
        // Nested audiobook folder should group with ebooks in same parent folder
        // Book Folder/
        // ├── Book.pdf
        // ├── Book.epub
        // └── Book Audiobook/  (folder-based audiobook)
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile pdf = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Book Folder")
                .fileName("Book.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Book Folder")
                .fileName("Book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        // Folder-based audiobook appears as a file with folderBased=true
        LibraryFile audiobookFolder = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Book Folder")
                .fileName("Book Audiobook")
                .bookFileType(BookFileType.AUDIOBOOK)
                .folderBased(true)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(pdf, epub, audiobookFolder));

        // All should be grouped together
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(pdf, epub, audiobookFolder);
    }

    @Test
    void groupByBaseName_shouldGroupAudiobookVariantNames() {
        // Different audiobook naming conventions should all group
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Hobbit")
                .fileName("The Hobbit.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile audiobook1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Hobbit")
                .fileName("The Hobbit Audiobook")
                .bookFileType(BookFileType.AUDIOBOOK)
                .folderBased(true)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, audiobook1));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, audiobook1);
    }

    // ========== Part Indicator Pattern Edge Cases ==========

    @Test
    void extractGroupingKey_shouldStripBareDiscAndCdAndPt() {
        // pt, disc, disk, cd are unambiguous and stripped even without brackets/dash
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit disc 1.m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit disk 2.m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit cd 3.m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit pt 1.m4b")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldStripPartInSquareBrackets() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [part 1].m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [Part 2].m4b")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldStripPartCaseInsensitive() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (PART 1).m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (Part 1).m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit - PART 1.m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit DISC 1.m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit CD 3.m4b")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldNotStripPartWithoutNumber() {
        // "part" keyword without a trailing number should not be stripped
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Best Part.epub")).isEqualTo("the best part");
        assertThat(BookFileGroupingUtils.extractGroupingKey("Disc Jockey.m4b")).isEqualTo("disc jockey");
    }

    @Test
    void extractGroupingKey_shouldStripAudioFormatIndicators() {
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (m4b).m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (mp3).mp3")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit [audiobook].m4b")).isEqualTo("the hobbit");
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (audio).m4b")).isEqualTo("the hobbit");
    }

    @Test
    void extractGroupingKey_shouldStripFormatThenPartIndicator() {
        // Both format indicator and part indicator present
        assertThat(BookFileGroupingUtils.extractGroupingKey("The Hobbit (mp3) (part 1).mp3")).isEqualTo("the hobbit");
    }

    // ========== Bare-Number Prefix Edge Cases ==========

    @Test
    void extractGroupingKey_shouldPreserveBareNumberDotPrefix() {
        // "N. Title" prefix is preserved in the grouping key (series detection handles it separately)
        assertThat(BookFileGroupingUtils.extractGroupingKey("01. The Lightning Thief.m4b")).isEqualTo("01. the lightning thief");
        assertThat(BookFileGroupingUtils.extractGroupingKey("100. War and Peace.epub")).isEqualTo("100. war and peace");
    }

    @Test
    void extractGroupingKey_shouldStripDashPrefixViaAuthorPattern() {
        // "01 - The Lightning Thief" looks like "number - Author Name" to the trailing author pattern,
        // so the author pattern strips it. This is expected existing behavior.
        assertThat(BookFileGroupingUtils.extractGroupingKey("01 - The Lightning Thief.m4b")).isEqualTo("01");
    }

    @Test
    void extractGroupingKey_shouldNotTreatFourDigitNumberAsPrefix() {
        // 4-digit numbers should not be treated as series prefixes
        assertThat(BookFileGroupingUtils.extractGroupingKey("1984.epub")).isEqualTo("1984");
    }

    @Test
    void extractGroupingKey_shouldNotTreatNumberWithoutSeparatorAsPrefix() {
        // "101 Dalmatians" has no dot or dash after the number
        assertThat(BookFileGroupingUtils.extractGroupingKey("101 Dalmatians.epub")).isEqualTo("101 dalmatians");
    }

    @Test
    void groupByBaseName_shouldSeparateFiveBookSeriesWithBareNumbers() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        List<LibraryFile> files = new java.util.ArrayList<>();
        String[] titles = {
                "1. The Lightning Thief.m4b",
                "2. The Sea of Monsters.m4b",
                "3. The Titans Curse.m4b",
                "4. The Battle of the Labyrinth.m4b",
                "5. The Last Olympian.m4b"
        };
        for (String title : titles) {
            files.add(LibraryFile.builder()
                    .libraryPathEntity(pathEntity)
                    .fileSubPath("Percy Jackson")
                    .fileName(title)
                    .bookFileType(BookFileType.AUDIOBOOK)
                    .build());
        }

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(files);

        assertThat(groups).hasSize(5);
    }

    @Test
    void groupByBaseName_shouldGroupSameBareNumberInMultipleFormats() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Percy Jackson")
                .fileName("1. The Lightning Thief.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile m4b = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Percy Jackson")
                .fileName("1. The Lightning Thief.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile epub2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Percy Jackson")
                .fileName("2. The Sea of Monsters.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, m4b, epub2));

        // Book 1 in 2 formats = 1 group, Book 2 = 1 group
        assertThat(groups).hasSize(2);
        boolean foundBook1Group = groups.values().stream()
                .anyMatch(list -> list.size() == 2 && list.containsAll(List.of(epub, m4b)));
        assertThat(foundBook1Group).isTrue();
    }

    @Test
    void groupByBaseName_shouldSeparateDashPrefixedSeriesFiles() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Rick Riordan")
                .fileName("01 - The Lightning Thief.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Rick Riordan")
                .fileName("02 - The Sea of Monsters.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldHandleRootLevelBareNumberedFiles() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("1. The Lightning Thief.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("2. The Sea of Monsters.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        // Root-level uses exact key matching, different keys = separate groups
        assertThat(groups).hasSize(2);
    }

    // ========== Series Detection with Various Keywords ==========

    @Test
    void groupByBaseName_shouldSeparateEpisodeSeriesEntries() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile ep1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Podcast")
                .fileName("The Podcast Episode 1.mp3")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile ep2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("The Podcast")
                .fileName("The Podcast Episode 2.mp3")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(ep1, ep2));

        assertThat(groups).hasSize(2);
    }

    @Test
    void groupByBaseName_shouldSeparateHashNumberedSeries() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile n1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Discworld")
                .fileName("Discworld #1.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile n2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Discworld")
                .fileName("Discworld #2.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(n1, n2));

        assertThat(groups).hasSize(2);
    }

    // ========== Multi-Part Grouping Edge Cases ==========

    @Test
    void groupByBaseName_shouldGroupBracketedPartsAtRootLevel() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile pt1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("The Lost Hero (part 1).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile pt2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath(null)
                .fileName("The Lost Hero (part 2).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(pt1, pt2));

        // Root-level exact key matching: both produce "the lost hero" → same group
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(pt1, pt2);
    }

    @Test
    void groupByBaseName_shouldGroupDiscVariantsOfSameBook() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile d1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("War and Peace")
                .fileName("War and Peace disc 1.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile d2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("War and Peace")
                .fileName("War and Peace disc 2.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile d3 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("War and Peace")
                .fileName("War and Peace disc 3.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(d1, d2, d3));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(d1, d2, d3);
    }

    @Test
    void groupByBaseName_shouldGroupCdVariantsOfSameBook() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile cd1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Moby Dick")
                .fileName("Moby Dick cd 1.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        LibraryFile cd2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Moby Dick")
                .fileName("Moby Dick cd 2.m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(cd1, cd2));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(cd1, cd2);
    }

    // ========== Grouping Boundary / Misc Edge Cases ==========

    @Test
    void groupByBaseName_shouldHandleEmptyList() {
        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of());
        assertThat(groups).isEmpty();
    }

    @Test
    void groupByBaseName_shouldHandleSingleFileInFolder() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Some Folder")
                .fileName("Lonely Book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file));

        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactly(file);
    }

    @Test
    void groupByBaseName_shouldHandleEmptySubPath() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile file1 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("Book.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile file2 = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("")
                .fileName("Book.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(file1, file2));

        // Empty subPath treated as root level
        assertThat(groups).hasSize(1);
    }

    @Test
    void groupByBaseName_shouldSeparateCompletelyUnrelatedFilesInFolder() {
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile war = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Random")
                .fileName("War and Peace.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile moby = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Random")
                .fileName("Moby Dick.epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile pride = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Random")
                .fileName("Pride and Prejudice.pdf")
                .bookFileType(BookFileType.PDF)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(war, moby, pride));

        assertThat(groups).hasSize(3);
    }

    @Test
    void groupByBaseName_shouldHandleFullHeroesOfOlympusScenario() {
        // Realistic 5-book series with multi-part splits for some books
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile lh1 = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("1. The Lost Hero (part 1).m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile lh2 = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("1. The Lost Hero (part 2).m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile son = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("2. The Son of Neptune.m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile moa1 = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("3. The Mark of Athena (part 1).m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile moa2 = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("3. The Mark of Athena (part 2).m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile hoh = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("4. The House of Hades.m4b").bookFileType(BookFileType.AUDIOBOOK).build();
        LibraryFile boo = LibraryFile.builder().libraryPathEntity(pathEntity)
                .fileSubPath("Heroes of Olympus").fileName("5. The Blood of Olympus.m4b").bookFileType(BookFileType.AUDIOBOOK).build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(lh1, lh2, son, moa1, moa2, hoh, boo));

        // 5 books: Lost Hero (2 parts grouped), Son of Neptune, Mark of Athena (2 parts grouped), House of Hades, Blood of Olympus
        assertThat(groups).hasSize(5);
        boolean lhGrouped = groups.values().stream().anyMatch(l -> l.size() == 2 && l.containsAll(List.of(lh1, lh2)));
        boolean moaGrouped = groups.values().stream().anyMatch(l -> l.size() == 2 && l.containsAll(List.of(moa1, moa2)));
        assertThat(lhGrouped).isTrue();
        assertThat(moaGrouped).isTrue();
    }

    @Test
    void groupByBaseName_shouldGroupFolderWithDotInName() {
        // Folder-based audiobook with dot in name like "1. American Gods - Neil Gaiman (2011)"
        // should group with ebooks in the same folder
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/library");

        LibraryFile epub = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("1. American Gods - Neil Gaiman (2011).epub")
                .bookFileType(BookFileType.EPUB)
                .build();

        LibraryFile mobi = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("1. American Gods - Neil Gaiman (2011).mobi")
                .bookFileType(BookFileType.MOBI)
                .build();

        LibraryFile m4b = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("1. American Gods - Neil Gaiman (2011).m4b")
                .bookFileType(BookFileType.AUDIOBOOK)
                .build();

        // Folder-based audiobook - note: fileName is folder name without extension
        LibraryFile mp3Folder = LibraryFile.builder()
                .libraryPathEntity(pathEntity)
                .fileSubPath("Neil Gaiman/American Gods")
                .fileName("1. American Gods - Neil Gaiman (2011)")
                .bookFileType(BookFileType.AUDIOBOOK)
                .folderBased(true)
                .build();

        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(List.of(epub, mobi, m4b, mp3Folder));

        // All 4 files should be grouped as one book
        assertThat(groups).hasSize(1);
        assertThat(groups.values().iterator().next()).containsExactlyInAnyOrder(epub, mobi, m4b, mp3Folder);
    }
}
