package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LibraryFileHelperTest {
    @TempDir
    Path tempDir;

    @InjectMocks
    private LibraryFileHelper libraryFileHelper;

    @Test
    void testGetAllLibraryFiles_IncludesAudiobookFolderFiles() throws IOException {
        Path authorFolder = tempDir.resolve("author");
        Path bookFolder = authorFolder.resolve("title");
        Path otherBookFolder = authorFolder.resolve("other");

        Files.createDirectories(bookFolder);
        Files.createDirectories(otherBookFolder);

        Files.write(bookFolder.resolve("a.mp3"), new byte[]{1});
        Files.write(bookFolder.resolve("b.mp3"), new byte[]{1});
        Files.write(otherBookFolder.resolve("c.mp3"), new byte[]{1});

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath(tempDir.toString());

        LibraryEntity testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .libraryPaths(List.of(libraryPath))
                .build();

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(testLibrary);

        List<String> actual = libraryFiles
                .stream()
                .map(LibraryFile::getFullPath)
                .map(p -> tempDir.relativize(p))
                .map(Path::toString)
                .sorted()
                .toList();

        List<String> expected = List.of(
                "author/other/c.mp3",
                "author/title"
        );

        assertEquals(expected, actual);
    }

    @Test
    void testGetLibraryFiles_HandlesInaccessibleDirectories() throws IOException {
        Files.write(tempDir.resolve("happy.epub"), new byte[]{1});
        Files.createDirectory(tempDir.resolve("some_other_random_named_dir"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("---------")));
        Files.write(tempDir.resolve("zzzz_happ.epub"), new byte[]{1});

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath(tempDir.toString());

        LibraryEntity testLibrary = LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .libraryPaths(List.of(libraryPath))
                .build();

        List<LibraryFile> libraryFiles = libraryFileHelper.getLibraryFiles(testLibrary);
        assertEquals(libraryFiles.stream().map(LibraryFile::getFileName).sorted().toList(), List.of("happy.epub", "zzzz_happ.epub"));
    }

    private LibraryEntity createLibrary(Path path) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath(path.toString());

        return LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .organizationMode(LibraryOrganizationMode.BOOK_PER_FILE)
                .libraryPaths(List.of(libraryPath))
                .build();
    }

    @Test
    void testGetLibraryFiles_skipsDirectoryWithIgnoreFile() throws IOException {
        Path ignoredDir = Files.createDirectories(tempDir.resolve("ignored-folder"));
        Files.createFile(ignoredDir.resolve(".ignore"));
        Files.write(ignoredDir.resolve("hidden-book.epub"), new byte[]{1});

        Files.write(tempDir.resolve("visible-book.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("visible-book.epub");
    }

    @Test
    void testGetLibraryFiles_doesNotSkipRootWithIgnoreFile() throws IOException {
        Files.createFile(tempDir.resolve(".ignore"));
        Files.write(tempDir.resolve("root-book.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("root-book.epub");
    }

    @Test
    void testGetLibraryFiles_ignoreFileOnlyAffectsItsDirectory() throws IOException {
        Path ignoredDir = Files.createDirectories(tempDir.resolve("ignored"));
        Files.createFile(ignoredDir.resolve(".ignore"));
        Files.write(ignoredDir.resolve("hidden.epub"), new byte[]{1});

        Path visibleDir = Files.createDirectories(tempDir.resolve("visible"));
        Files.write(visibleDir.resolve("book.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("book.epub");
    }

    @Test
    void testGetLibraryFiles_skipsZeroByteFiles() throws IOException {
        Files.createFile(tempDir.resolve("empty.epub"));
        Files.write(tempDir.resolve("real.epub"), new byte[]{1, 2, 3});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("real.epub");
    }

    @Test
    void testGetLibraryFiles_includesNonZeroByteFiles() throws IOException {
        Files.write(tempDir.resolve("book1.epub"), new byte[]{1});
        Files.write(tempDir.resolve("book2.pdf"), new byte[]{1, 2});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(2);
    }

    @Test
    void recognizesAllSupportedExtensions() throws IOException {
        for (String ext : List.of("pdf", "epub", "cbz", "cbr", "cb7", "mobi", "azw3", "azw", "fb2", "m4b", "m4a", "mp3")) {
            Files.write(tempDir.resolve("book." + ext), new byte[]{1});
        }

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(12);
    }

    @Test
    void ignoresUnrecognizedExtensions() throws IOException {
        Files.write(tempDir.resolve("book.lit"), new byte[]{1});
        Files.write(tempDir.resolve("book.lrf"), new byte[]{1});
        Files.write(tempDir.resolve("book.txt"), new byte[]{1});
        Files.write(tempDir.resolve("cover.jpg"), new byte[]{1});
        Files.write(tempDir.resolve("real.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("real.epub");
    }

    @Test
    void classifiesEbookAndAudiobookTypes() throws IOException {
        Files.write(tempDir.resolve("book.epub"), new byte[]{1});
        Files.write(tempDir.resolve("book.m4b"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));
        assertThat(files).hasSize(2);

        LibraryFile epub = files.stream().filter(f -> f.getFileName().equals("book.epub")).findFirst().orElseThrow();
        LibraryFile m4b = files.stream().filter(f -> f.getFileName().equals("book.m4b")).findFirst().orElseThrow();

        assertThat(epub.getBookFileType()).isEqualTo(BookFileType.EPUB);
        assertThat(m4b.getBookFileType()).isEqualTo(BookFileType.AUDIOBOOK);
    }

    @Test
    void collapsesMultipleAudioFilesInSubfolderToFolderBased() throws IOException {
        Path audioDir = Files.createDirectories(tempDir.resolve("Author/Book/audiobook"));
        for (int i = 1; i <= 5; i++) {
            Files.write(audioDir.resolve("chapter" + i + ".mp3"), new byte[]{1});
        }

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().isFolderBased()).isTrue();
        assertThat(files.getFirst().getBookFileType()).isEqualTo(BookFileType.AUDIOBOOK);
        assertThat(files.getFirst().getFileName()).isEqualTo("audiobook");
    }

    @Test
    void singleAudioFileInSubfolderStaysIndividual() throws IOException {
        Path audioDir = Files.createDirectories(tempDir.resolve("Author/Book"));
        Files.write(audioDir.resolve("standalone.m4b"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().isFolderBased()).isFalse();
        assertThat(files.getFirst().getFileName()).isEqualTo("standalone.m4b");
    }

    @Test
    void rootLevelAudioFilesNeverCollapse() throws IOException {
        Files.write(tempDir.resolve("track1.mp3"), new byte[]{1});
        Files.write(tempDir.resolve("track2.mp3"), new byte[]{1});
        Files.write(tempDir.resolve("track3.mp3"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(3);
        assertThat(files).noneMatch(LibraryFile::isFolderBased);
    }

    @Test
    void structure3_chapterMp3sCollapsePerSubfolder() throws IOException {
        Path parent = Files.createDirectories(tempDir.resolve("J K Rowling - HP Audiobooks"));
        Path book1 = Files.createDirectories(parent.resolve("1 Philosopher's Stone"));
        Path book2 = Files.createDirectories(parent.resolve("2 Chamber of Secrets"));

        for (int i = 1; i <= 17; i++) {
            Files.write(book1.resolve(String.format("CH%02d.mp3", i)), new byte[]{1});
        }
        for (int i = 1; i <= 18; i++) {
            Files.write(book2.resolve(String.format("CH%02d.mp3", i)), new byte[]{1});
        }

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(2);
        assertThat(files).allMatch(LibraryFile::isFolderBased);
        assertThat(files).allMatch(f -> f.getBookFileType() == BookFileType.AUDIOBOOK);
    }

    @Test
    void structure4_ebooksAndAudiobookSubfolderDiscoveredCorrectly() throws IOException {
        Path catchingFire = Files.createDirectories(tempDir.resolve("Suzanne Collins/Catching Fire"));
        Files.write(catchingFire.resolve("Catching Fire - Suzanne Collins.azw3"), new byte[]{1});
        Files.write(catchingFire.resolve("Catching Fire - Suzanne Collins.epub"), new byte[]{1});

        Path audioDir = Files.createDirectories(catchingFire.resolve("audiobook"));
        for (int i = 1; i <= 10; i++) {
            Files.write(audioDir.resolve(String.format("Catching Fire %03d.mp3", i)), new byte[]{1});
        }

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        long ebookCount = files.stream().filter(f -> f.getBookFileType() != BookFileType.AUDIOBOOK).count();
        long audiobookCount = files.stream().filter(f -> f.getBookFileType() == BookFileType.AUDIOBOOK).count();
        long folderBasedCount = files.stream().filter(LibraryFile::isFolderBased).count();

        assertThat(ebookCount).isEqualTo(2);
        assertThat(audiobookCount).isEqualTo(1);
        assertThat(folderBasedCount).isEqualTo(1);
        assertThat(files).hasSize(3);
    }

    @Test
    void structure1_flatRootFilesAllDiscovered() throws IOException {
        Files.write(tempDir.resolve("Salem's Lot.epub"), new byte[]{1});
        Files.write(tempDir.resolve("Good Spirits.epub"), new byte[]{1});
        Files.write(tempDir.resolve("Hansel and Gretel.epub"), new byte[]{1});
        Files.write(tempDir.resolve("Hansel and Gretel.m4b"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(4);
        assertThat(files).noneMatch(LibraryFile::isFolderBased);
        assertThat(files).allMatch(f -> f.getFileSubPath() == null || f.getFileSubPath().isEmpty());
    }

    @Test
    void nestedFiles_haveCorrectSubPaths() throws IOException {
        Path titleDir = Files.createDirectories(tempDir.resolve("Andy Weir/The Martian"));
        Files.write(titleDir.resolve("The Martian.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileSubPath()).isEqualTo("Andy Weir/The Martian");
    }

    @Test
    void ignoresHiddenFiles() throws IOException {
        Files.write(tempDir.resolve(".DS_Store"), new byte[]{1});
        Files.write(tempDir.resolve(".hidden.epub"), new byte[]{1});
        Files.write(tempDir.resolve("visible.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("visible.epub");
    }

    @Test
    void ignoresTempFiles() throws IOException {
        Files.write(tempDir.resolve("book.epub.part"), new byte[]{1});
        Files.write(tempDir.resolve("book.epub.tmp"), new byte[]{1});
        Files.write(tempDir.resolve("book.epub.crdownload"), new byte[]{1});
        Files.write(tempDir.resolve("real.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
    }

    @Test
    void ignoresSystemDirectories() throws IOException {
        for (String sysDir : List.of("#recycle", "@eaDir", ".caltrash")) {
            Path dir = Files.createDirectories(tempDir.resolve(sysDir));
            Files.write(dir.resolve("book.epub"), new byte[]{1});
        }
        Files.write(tempDir.resolve("real.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName()).isEqualTo("real.epub");
    }

    private LibraryEntity createLibraryWithMode(Path path, LibraryOrganizationMode mode) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath(path.toString());

        return LibraryEntity.builder()
                .name("Test Library")
                .icon("book")
                .watch(false)
                .organizationMode(mode)
                .libraryPaths(List.of(libraryPath))
                .build();
    }

    @Test
    void autoDetect_collapsesAudiobookSubfolderSameAsOtherModes() throws IOException {
        Path audioDir = Files.createDirectories(tempDir.resolve("HP Book 1"));
        for (int i = 1; i <= 5; i++) {
            Files.write(audioDir.resolve("ch" + i + ".mp3"), new byte[]{1});
        }

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibraryWithMode(tempDir, LibraryOrganizationMode.AUTO_DETECT));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().isFolderBased()).isTrue();
    }

    @Test
    void autoDetect_singleAudioFileStaysIndividual() throws IOException {
        Path dir = Files.createDirectories(tempDir.resolve("Book"));
        Files.write(dir.resolve("book.m4b"), new byte[]{1});
        Files.write(dir.resolve("book.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibraryWithMode(tempDir, LibraryOrganizationMode.AUTO_DETECT));

        assertThat(files).hasSize(2);
        assertThat(files).noneMatch(LibraryFile::isFolderBased);
    }

    @Test
    void deeplyNestedFilesAreDiscovered() throws IOException {
        Path deep = Files.createDirectories(tempDir.resolve("A/B/C/D/E"));
        Files.write(deep.resolve("deep.epub"), new byte[]{1});

        List<LibraryFile> files = libraryFileHelper.getLibraryFiles(createLibrary(tempDir));

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileSubPath()).isEqualTo("A/B/C/D/E");
    }

    @Test
    void detectDeletedBookIds_shouldIgnoreBooksWithoutFiles() {
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(Collections.emptyList())
                .build();

        List<Long> actual = libraryFileHelper.detectDeletedBookIds(Collections.emptyList(), List.of(book));

        assertThat(actual).hasSize(0);
    }

    @Test
    void detectDeletedBookIds_shouldIgnoreDeletedBooks() {
        BookEntity book = BookEntity.builder()
                .id(1L)
                .bookFiles(List.of(BookFileEntity.builder().build()))
                .deleted(true)
                .build();

        List<Long> actual = libraryFileHelper.detectDeletedBookIds(Collections.emptyList(), List.of(book));

        assertThat(actual).hasSize(0);
    }

    @Test
    void detectDeletedBookIds_shouldHandleFolderBasedAudiobooks() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile bookLibraryFolder = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .folderBased(true)
                .fileSubPath("example")
                .fileName("a")
                .build();

        BookFileEntity existing = BookFileEntity.builder()
                .folderBased(true)
                .fileSubPath("example")
                .fileName("a")
                .build();

        BookEntity book = BookEntity.builder()
                .id(1L)
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(existing))
                .build();

        existing.setBook(book);

        List<Long> actual = libraryFileHelper.detectDeletedBookIds(
                List.of(bookLibraryFolder),
                List.of(book)
        );

        assertThat(actual).hasSize(0);
    }

    @Test
    void detectDeletedBookIds_shouldOnlyDetectBooksMissingFiles() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile bookLibraryFile = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("file.epub")
                .build();

        BookFileEntity existing = BookFileEntity.builder()
                .fileSubPath("example")
                .fileName("file.epub")
                .build();

        BookEntity book = BookEntity.builder()
                .id(1L)
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(existing))
                .build();

        existing.setBook(book);

        BookFileEntity expectedMissingBookFileEntity = BookFileEntity.builder()
                .fileSubPath("missing")
                .fileName("missing.epub")
                .build();

        BookEntity expectedMissingBook = BookEntity.builder()
                .id(2L)
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(expectedMissingBookFileEntity))
                .build();

        expectedMissingBookFileEntity.setBook(expectedMissingBook);

        List<Long> actual = libraryFileHelper.detectDeletedBookIds(
                List.of(bookLibraryFile),
                List.of(book, expectedMissingBook)
        );

        assertThat(actual).isEqualTo(List.of(2L));
    }

    @Test
    void detectNewBooks_shouldReturnAllFilesWhenNoBooks() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile fileA = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("a.epub")
                .build();
        LibraryFile fileB = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("b.epub")
                .build();

        List<LibraryFile> actual = libraryFileHelper.detectNewBookPaths(
                List.of(fileA, fileB),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(actual).isEqualTo(List.of(fileA, fileB));
    }

    @Test
    void detectNewBooks_shouldNotReturnFilesAssociatedWithBooks() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile fileA = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("a.epub")
                .build();
        LibraryFile fileB = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("b.epub")
                .build();

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileSubPath("example")
                .fileName("a.epub")
                .build();

        BookEntity book = BookEntity.builder()
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(bookFile))
                .build();

        bookFile.setBook(book);

        List<LibraryFile> actual = libraryFileHelper.detectNewBookPaths(
                List.of(fileA, fileB),
                List.of(book),
                Collections.emptyList()
        );

        assertThat(actual).isEqualTo(List.of(fileB));
    }

    @Test
    void detectNewBooks_shouldHandleFolderAudiobooks() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile folderA = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .folderBased(true)
                .fileSubPath("example")
                .fileName("a")
                .build();

        LibraryFile fileB = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("b.epub")
                .build();

        BookFileEntity audiobookFolder = BookFileEntity.builder()
                .folderBased(true)
                .fileSubPath("example")
                .fileName("a")
                .build();

        BookEntity audiobook = BookEntity.builder()
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(audiobookFolder))
                .build();

        audiobookFolder.setBook(audiobook);

        List<LibraryFile> actual = libraryFileHelper.detectNewBookPaths(
                List.of(folderA, fileB),
                List.of(audiobook),
                Collections.emptyList()
        );

        assertThat(actual).isEqualTo(List.of(fileB));
    }


    @Test
    void detectNewBooks_shouldHandleAdditionalFiles() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile fileA = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("a.epub")
                .build();
        LibraryFile fileB = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("b.epub")
                .build();
        LibraryFile fileC = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("c.epub")
                .build();

        BookFileEntity bookFile = BookFileEntity.builder()
                .fileSubPath("example")
                .fileName("a.epub")
                .build();

        BookEntity book = BookEntity.builder()
                .libraryPath(libraryPathEntity)
                .bookFiles(List.of(bookFile))
                .build();

        bookFile.setBook(book);

        BookFileEntity additionalFile = BookFileEntity.builder()
                .fileSubPath("example")
                .fileName("b.epub")
                .book(book)
                .build();

        List<LibraryFile> actual = libraryFileHelper.detectNewBookPaths(
                List.of(fileA, fileB, fileC),
                List.of(book),
                List.of(additionalFile)
        );

        assertThat(actual).isEqualTo(List.of(fileC));
    }

    @Test
    void detectDeletedAdditionalFiles_shouldIgnoreNonBookFormat() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        BookEntity book = BookEntity.builder()
                .libraryPath(libraryPathEntity)
                .build();

        BookFileEntity additionalFile = BookFileEntity.builder()
                .id(1L)
                .isBookFormat(false)
                .fileSubPath("example")
                .fileName("b.epub")
                .book(book)
                .build();

        List<Long> actual = libraryFileHelper.detectDeletedAdditionalFiles(
                Collections.emptyList(),
                List.of(additionalFile)
        );

        assertThat(actual).hasSize(0);
    }

    @Test
    void detectDeletedAdditionalFiles_shouldFindMissingAdditionalFiles() {
        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path("/books")
                .build();

        LibraryFile fileA = LibraryFile.builder()
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath("example")
                .fileName("a.epub")
                .build();

        BookEntity book = BookEntity.builder()
                .libraryPath(libraryPathEntity)
                .build();

        BookFileEntity additionalFileA = BookFileEntity.builder()
                .id(1L)
                .isBookFormat(true)
                .fileSubPath("example")
                .fileName("a.epub")
                .book(book)
                .build();

        BookFileEntity additionalFileB = BookFileEntity.builder()
                .id(2L)
                .isBookFormat(true)
                .fileSubPath("example")
                .fileName("b.epub")
                .book(book)
                .build();

        List<Long> actual = libraryFileHelper.detectDeletedAdditionalFiles(
                List.of(fileA),
                List.of(additionalFileA, additionalFileB)
        );

        assertThat(actual).isEqualTo(List.of(2L));
    }
}
