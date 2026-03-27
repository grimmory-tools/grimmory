package org.booklore.util;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @TempDir
    Path tempDir;

    private BookEntity createBookEntity(Path libraryPath, String subPath, String fileName) {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setPath(libraryPath.toString());

        BookEntity bookEntity = new BookEntity();
        bookEntity.setLibraryPath(libraryPathEntity);

        BookFileEntity bookFileEntity = new BookFileEntity();
        bookFileEntity.setBook(bookEntity);
        bookFileEntity.setFileSubPath(subPath);
        bookFileEntity.setFileName(fileName);
        bookEntity.setBookFiles(List.of(bookFileEntity));

        return bookEntity;
    }

    @Test
    void testGetBookFullPath() {
        Path libraryPath = tempDir;
        String subPath = "sub/folder";
        String fileName = "test.pdf";

        BookEntity book = createBookEntity(libraryPath, subPath, fileName);

        Path fullPath = FileUtils.getBookFullPath(book);

        Path expected = libraryPath.resolve(subPath).resolve(fileName);

        assertEquals(expected, fullPath);
    }

    @Test
    void testGetRelativeSubPath() {
        Path base = tempDir;
        Path nested = base.resolve("a/b/c/file.txt");

        String relative = FileUtils.getRelativeSubPath(base.toString(), nested);

        assertEquals("a/b/c", relative);
    }

    @Test
    void testGetRelativeSubPath_noParent() {
        Path base = tempDir;
        Path file = base.resolve("file.txt");

        String result = FileUtils.getRelativeSubPath(base.toString(), file);

        assertEquals("", result);
    }

    @Test
    void testGetFileSizeInKb_path() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        byte[] content = new byte[2048]; // 2 KB
        Files.write(file, content);

        Long size = FileUtils.getFileSizeInKb(file);

        assertEquals(2, size);
    }

    @Test
    void testGetFileSizeInKb_pathFileNotFound() {
        Path file = tempDir.resolve("missing.txt");

        Long size = FileUtils.getFileSizeInKb(file);

        assertNull(size);
    }

    @Test
    void testGetFileSizeInKb_bookEntity() throws IOException {
        Path library = tempDir.resolve("lib");
        Files.createDirectories(library);

        String sub = "files";
        Path subFolder = library.resolve(sub);
        Files.createDirectories(subFolder);

        Path file = subFolder.resolve("book.epub");
        Files.write(file, new byte[4096]); // 4 KB

        BookEntity book = createBookEntity(library, sub, "book.epub");

        Long size = FileUtils.getFileSizeInKb(book);

        assertEquals(4, size);
    }

    @Test
    void testDeleteDirectoryRecursively() throws IOException {
        Path dir = tempDir.resolve("deleteMe");
        Files.createDirectories(dir);

        Files.write(dir.resolve("file1.txt"), "data".getBytes());
        Files.createDirectories(dir.resolve("nested"));
        Files.write(dir.resolve("nested/file2.txt"), "more".getBytes());

        assertTrue(Files.exists(dir));

        FileUtils.deleteDirectoryRecursively(dir);

        assertFalse(Files.exists(dir));
    }

    @Test
    void testDeleteDirectoryRecursively_nonExistentPath() {
        Path nonExistent = tempDir.resolve("doesNotExist");
        assertDoesNotThrow(() -> FileUtils.deleteDirectoryRecursively(nonExistent));
    }

    @Test
    void testShouldIgnore_hiddenFile_returnsTrue() {
        Path hiddenFile = tempDir.resolve(".hidden");
        assertTrue(FileUtils.shouldIgnore(hiddenFile));
    }

    @Test
    void testShouldIgnore_hiddenDirectory_returnsTrue() {
        Path hiddenDir = tempDir.resolve(".git");
        assertTrue(FileUtils.shouldIgnore(hiddenDir));
    }

    @Test
    void testShouldIgnore_normalFile_returnsFalse() {
        Path normalFile = tempDir.resolve("normal.txt");
        assertFalse(FileUtils.shouldIgnore(normalFile));
    }

    @Test
    void testShouldIgnore_pathWithCaltrash_returnsTrue() {
        Path caltrashPath = tempDir.resolve(".caltrash").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_pathWithCaltrashInSubdirectory_returnsTrue() {
        Path caltrashPath = tempDir.resolve("subdir").resolve(".caltrash").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_pathWithoutCaltrash_returnsFalse() {
        Path normalPath = tempDir.resolve("subdir").resolve("file.txt");
        assertFalse(FileUtils.shouldIgnore(normalPath));
    }

    @Test
    void testShouldIgnore_pathWithRecycle_returnsTrue() {
        Path caltrashPath = tempDir.resolve("#recycle").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_pathWithRecycleInSubdirectory_returnsTrue() {
        Path caltrashPath = tempDir.resolve("subdir").resolve("#recycle").resolve("file.txt");
        assertTrue(FileUtils.shouldIgnore(caltrashPath));
    }

    @Test
    void testShouldIgnore_emptyFileName_returnsFalse() {
        Path emptyPath = tempDir.resolve("");
        assertFalse(FileUtils.shouldIgnore(emptyPath));
    }

    @Test
    void testShouldIgnore_tempFileExtensions_returnsTrue() {
        List<String> tempFiles = List.of(
                "book.epub.part", "book.epub.tmp", "book.epub.crdownload",
                "book.epub.download", "book.epub.bak", "book.epub.old",
                "book.epub.temp", "book.epub.tempfile"
        );
        for (String name : tempFiles) {
            assertTrue(FileUtils.shouldIgnore(tempDir.resolve(name)), "Should ignore: " + name);
        }
    }

    @Test
    void testShouldIgnore_standaloneTempFile_returnsTrue() {
        assertTrue(FileUtils.shouldIgnore(tempDir.resolve("something.tmp")));
        assertTrue(FileUtils.shouldIgnore(tempDir.resolve("download.part")));
    }

    @Test
    void testShouldIgnore_tempExtensionCaseInsensitive_returnsTrue() {
        assertTrue(FileUtils.shouldIgnore(tempDir.resolve("book.epub.PART")));
        assertTrue(FileUtils.shouldIgnore(tempDir.resolve("book.epub.TMP")));
    }

    @Test
    void testShouldIgnore_normalBookFile_returnsFalse() {
        assertFalse(FileUtils.shouldIgnore(tempDir.resolve("book.epub")));
        assertFalse(FileUtils.shouldIgnore(tempDir.resolve("book.pdf")));
        assertFalse(FileUtils.shouldIgnore(tempDir.resolve("audiobook.m4b")));
    }

    @Test
    void testGetFileSizeInKb_validFile_returnsSize() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.write(file, "test".getBytes());

        Long size = FileUtils.getFileSizeInKb(file);
        assertNotNull(size, "File size should not be null for existing file");
        assertTrue(size >= 0, "File size should be non-negative");
    }

    // ========== isSeriesFolder Tests ==========

    // ========== findCoverImageInFolder Tests ==========

    @Test
    void testFindCoverImageInFolder_coverJpg_found() throws IOException {
        Files.createFile(tempDir.resolve("cover.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_coverPng_found() throws IOException {
        Files.createFile(tempDir.resolve("cover.png"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.png", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_imageJpg_found() throws IOException {
        Files.createFile(tempDir.resolve("image.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("image.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_folderJpg_found() throws IOException {
        Files.createFile(tempDir.resolve("folder.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("folder.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_coverPrioritizedOverFolder() throws IOException {
        Files.createFile(tempDir.resolve("folder.jpg"));
        Files.createFile(tempDir.resolve("cover.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_coverPrioritizedOverImage() throws IOException {
        Files.createFile(tempDir.resolve("image.png"));
        Files.createFile(tempDir.resolve("cover.png"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.png", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_folderPrioritizedOverImage() throws IOException {
        Files.createFile(tempDir.resolve("image.jpg"));
        Files.createFile(tempDir.resolve("folder.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("folder.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_jpgPrioritizedOverPng() throws IOException {
        Files.createFile(tempDir.resolve("cover.png"));
        Files.createFile(tempDir.resolve("cover.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.jpg", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_webpSupported() throws IOException {
        Files.createFile(tempDir.resolve("cover.webp"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isPresent());
        assertEquals("cover.webp", result.get().getFileName().toString());
    }

    @Test
    void testFindCoverImageInFolder_noCoverImage_returnsEmpty() throws IOException {
        Files.createFile(tempDir.resolve("book.epub"));
        Files.createFile(tempDir.resolve("metadata.opf"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_emptyFolder_returnsEmpty() {
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_nonExistentPath_returnsEmpty() {
        var result = FileUtils.findCoverImageInFolder(tempDir.resolve("nonexistent"));
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_nullPath_returnsEmpty() {
        var result = FileUtils.findCoverImageInFolder(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_unsupportedExtension_returnsEmpty() throws IOException {
        Files.createFile(tempDir.resolve("cover.tiff"));
        Files.createFile(tempDir.resolve("cover.svg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_directoryNamedCover_ignored() throws IOException {
        Files.createDirectories(tempDir.resolve("cover.jpg"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindCoverImageInFolder_unrelatedImageName_returnsEmpty() throws IOException {
        Files.createFile(tempDir.resolve("artwork.jpg"));
        Files.createFile(tempDir.resolve("poster.png"));
        var result = FileUtils.findCoverImageInFolder(tempDir);
        assertTrue(result.isEmpty());
    }

    // ========== isSeriesFolder Tests ==========

    @Test
    void testIsSeriesFolder_distinctTitles_returnsTrue() {
        List<Path> files = List.of(
                Path.of("1. The Lightning Thief.m4b"),
                Path.of("2. The Sea of Monsters.m4b"),
                Path.of("3. The Titans Curse.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_chapterFiles_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Chapter 01.mp3"),
                Path.of("Chapter 02.mp3"),
                Path.of("Chapter 03.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_bareNumberedTracks_returnsFalse() {
        List<Path> files = List.of(
                Path.of("01.mp3"),
                Path.of("02.mp3"),
                Path.of("03.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_trackFiles_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Track 01.mp3"),
                Path.of("Track 02.mp3"),
                Path.of("Track 03.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_mixedWithParts_returnsTrue() {
        List<Path> files = List.of(
                Path.of("1. The Lost Hero (part 1).m4b"),
                Path.of("1. The Lost Hero (part 2).m4b"),
                Path.of("2. The Son of Neptune.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_singleFile_returnsFalse() {
        List<Path> files = List.of(Path.of("The Lightning Thief.m4b"));
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_dashSeparatedNumbers_returnsTrue() {
        List<Path> files = List.of(
                Path.of("01 - The Lightning Thief.m4b"),
                Path.of("02 - The Sea of Monsters.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_emptyList_returnsFalse() {
        assertFalse(FileUtils.isSeriesFolder(List.of()));
    }

    @Test
    void testIsSeriesFolder_allSameTitle_returnsFalse() {
        // Same book split across multiple files (no number prefix)
        List<Path> files = List.of(
                Path.of("The Lightning Thief.m4b"),
                Path.of("The Lightning Thief.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_sameTitleWithParts_returnsFalse() {
        // Same book split into parts: after stripping part indicators, all produce same title
        List<Path> files = List.of(
                Path.of("The Lightning Thief (part 1).m4b"),
                Path.of("The Lightning Thief (part 2).m4b"),
                Path.of("The Lightning Thief (part 3).m4b")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_genericPartTitles_returnsFalse() {
        // "Part" is in the generic list, so "Part 1", "Part 2" → stripped to "part" → generic
        List<Path> files = List.of(
                Path.of("Part 1.mp3"),
                Path.of("Part 2.mp3"),
                Path.of("Part 3.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_genericDiscTitles_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Disc 1.mp3"),
                Path.of("Disc 2.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_genericSideTitles_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Side 1.mp3"),
                Path.of("Side 2.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_genericIntroEpilogue_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Intro.mp3"),
                Path.of("Epilogue.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_genericOutro_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Intro.mp3"),
                Path.of("Outro.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_mixedGenericAndRealTitles_returnsFalse() {
        // Only one non-generic title → not > 1 distinct → not a series
        List<Path> files = List.of(
                Path.of("Intro.mp3"),
                Path.of("Chapter 01.mp3"),
                Path.of("The Lightning Thief.m4b")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_mixedCaseFileNames_returnsTrue() {
        List<Path> files = List.of(
                Path.of("1. THE LIGHTNING THIEF.M4B"),
                Path.of("2. the Sea of Monsters.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_noExtension_returnsFalse() {
        List<Path> files = List.of(
                Path.of("The Lightning Thief"),
                Path.of("The Sea of Monsters")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_zeroPaddedNumbers_returnsTrue() {
        List<Path> files = List.of(
                Path.of("001. A Game of Thrones.m4b"),
                Path.of("002. A Clash of Kings.m4b"),
                Path.of("003. A Storm of Swords.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_numberedPartsOfSameBook_returnsFalse() {
        // Same book number but different parts → single distinct title after stripping
        List<Path> files = List.of(
                Path.of("1. The Lost Hero (part 1).m4b"),
                Path.of("1. The Lost Hero (part 2).m4b"),
                Path.of("1. The Lost Hero (part 3).m4b")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_twoDistinctAmongManyParts_returnsTrue() {
        List<Path> files = List.of(
                Path.of("1. The Lost Hero (part 1).m4b"),
                Path.of("1. The Lost Hero (part 2).m4b"),
                Path.of("1. The Lost Hero (part 3).m4b"),
                Path.of("2. The Son of Neptune (part 1).m4b"),
                Path.of("2. The Son of Neptune (part 2).m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_titlesWithSpecialCharacters_returnsTrue() {
        List<Path> files = List.of(
                Path.of("1. The Lion, the Witch & the Wardrobe.m4b"),
                Path.of("2. Prince Caspian.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_descriptiveChapterNames_returnsFalse() {
        List<Path> files = List.of(
                Path.of("CH01 THE BOY WHO LIVED.mp3"),
                Path.of("CH02 THE VANISHING GLASS.mp3"),
                Path.of("CH03 THE LETTERS FROM NO ONE.mp3"),
                Path.of("CH04 THE KEEPER OF THE KEYS.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_numberedChaptersWithTitles_returnsFalse() {
        List<Path> files = List.of(
                Path.of("Suzanne Collins - Catching Fire 001.mp3"),
                Path.of("Suzanne Collins - Catching Fire 002.mp3"),
                Path.of("Suzanne Collins - Catching Fire 003.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_prologueAndChapters_returnsFalse() {
        // All generic: prologue, chapter, epilogue
        List<Path> files = List.of(
                Path.of("Prologue.mp3"),
                Path.of("Chapter 01.mp3"),
                Path.of("Chapter 02.mp3"),
                Path.of("Chapter 03.mp3"),
                Path.of("Epilogue.mp3")
        );
        assertFalse(FileUtils.isSeriesFolder(files));
    }

    @Test
    void testIsSeriesFolder_largeSeriesFolder_returnsTrue() {
        List<Path> files = List.of(
                Path.of("01. The Colour of Magic.m4b"),
                Path.of("02. The Light Fantastic.m4b"),
                Path.of("03. Equal Rites.m4b"),
                Path.of("04. Mort.m4b"),
                Path.of("05. Sourcery.m4b"),
                Path.of("06. Wyrd Sisters.m4b"),
                Path.of("07. Pyramids.m4b"),
                Path.of("08. Guards Guards.m4b"),
                Path.of("09. Eric.m4b"),
                Path.of("10. Moving Pictures.m4b")
        );
        assertTrue(FileUtils.isSeriesFolder(files));
    }
}
