package com.adityachandel.booklore;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileMoveService;
import com.adityachandel.booklore.service.library.LibraryService;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileMoveServiceMoveFilesTest {

    @Mock
    private BookQueryService bookQueryService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MonitoringService monitoringService;

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private LibraryService libraryService;

    @InjectMocks
    private FileMoveService fileMoveService;

    @TempDir
    Path tempLibraryRoot;

    private BookEntity createBookWithFile(Path libraryRoot, String fileSubPath, String fileName) throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(42L)
                .name("Test Library")
                .fileNamingPattern(null)
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(libraryRoot.toString())
                .library(library)
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("Test Book")
                .authors(new HashSet<>(List.of(new AuthorEntity(1L, "Author Name", new ArrayList<>())))
                )
                .publishedDate(LocalDate.of(2020, 1, 1))
                .build();

        BookEntity book = BookEntity.builder()
                .id(1L)
                .fileName(fileName)
                .fileSubPath(fileSubPath)
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFilePath = book.getFullFilePath();
        Files.createDirectories(oldFilePath.getParent());
        Files.createFile(oldFilePath);

        return book;
    }

    private BookAdditionalFileEntity createAdditionalFile(BookEntity book, Long id, String fileName, 
                                                          String fileSubPath, AdditionalFileType type,
                                                          boolean createActualFile) throws IOException {
        BookAdditionalFileEntity additionalFile = BookAdditionalFileEntity.builder()
                .id(id)
                .book(book)
                .fileName(fileName)
                .fileSubPath(fileSubPath)
                .additionalFileType(type)
                .build();

        if (createActualFile) {
            Path filePath = Paths.get(book.getLibraryPath().getPath())
                    .resolve(fileSubPath)
                    .resolve(fileName);
            Files.createDirectories(filePath.getParent());
            Files.createFile(filePath);
        }

        return additionalFile;
    }

    @Test
    void testMoveFiles_skipsNonexistentFile() {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NoFile")
                .build();

        LibraryEntity library = LibraryEntity.builder()
                .id(43L)
                .name("Test Library")
                .fileNamingPattern("Moved/{title}")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .library(library)
                .build();

        BookEntity book = BookEntity.builder()
                .id(2L)
                .fileName("nofile.epub")
                .fileSubPath("subfolder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(2L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(2L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NoFile.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("No file should be created for nonexistent source")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithoutLibraryPath() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("MissingLibrary")
                .build();

        BookEntity book = BookEntity.builder()
                .id(3L)
                .fileName("missinglibrary.epub")
                .fileSubPath("subfolder")
                .metadata(metadata)
                .libraryPath(null)
                .build();

        Path fakeOldFile = tempLibraryRoot.resolve("subfolder").resolve("missinglibrary.epub");
        Files.createDirectories(fakeOldFile.getParent());
        Files.createFile(fakeOldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(3L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(3L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("MissingLibrary.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if library path is missing")
                .isFalse();
        assertThat(Files.exists(fakeOldFile))
                .withFailMessage("Original file should remain when library path is missing")
                .isTrue();

        Files.deleteIfExists(fakeOldFile);
    }

    @Test
    void testMoveFiles_skipsBookWithNullFileName() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NullFileName")
                .build();

        LibraryEntity library = LibraryEntity.builder()
                .id(46L)
                .name("Test Library")
                .fileNamingPattern("Moved/{title}")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .library(library)
                .build();

        BookEntity book = BookEntity.builder()
                .id(10L)
                .fileName(null)
                .fileSubPath("folder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(10L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(10L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NullFileName");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if filename is null")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithEmptyFileName() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("EmptyFileName")
                .build();

        LibraryEntity library = LibraryEntity.builder()
                .id(47L)
                .name("Test Library")
                .fileNamingPattern("Moved/{title}")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .library(library)
                .build();

        BookEntity book = BookEntity.builder()
                .id(11L)
                .fileName("")
                .fileSubPath("folder")
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(11L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(11L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("EmptyFileName");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if filename is empty")
                .isFalse();
    }

    @Test
    void testMoveFiles_skipsBookWithNullFileSubPath() throws IOException {
        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .title("NullSubPath")
                .build();

        LibraryEntity library = LibraryEntity.builder()
                .id(48L)
                .name("Test Library")
                .fileNamingPattern("Moved/{title}")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .library(library)
                .build();

        BookEntity book = BookEntity.builder()
                .id(12L)
                .fileName("file.epub")
                .fileSubPath(null)
                .metadata(metadata)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFile = tempLibraryRoot.resolve("file.epub");
        Files.createFile(oldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(12L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(12L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve("NullSubPath.epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if fileSubPath is null")
                .isFalse();

        Files.deleteIfExists(oldFile);
    }

    @Test
    void testMoveFiles_skipsBookWithNullMetadata() throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(49L)
                .name("Test Library")
                .fileNamingPattern("Moved/{title}")
                .build();

        LibraryPathEntity libraryPathEntity = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString())
                .library(library)
                .build();

        BookEntity book = BookEntity.builder()
                .id(13L)
                .fileName("file.epub")
                .fileSubPath("folder")
                .metadata(null)
                .libraryPath(libraryPathEntity)
                .build();

        Path oldFile = tempLibraryRoot.resolve("folder").resolve("file.epub");
        Files.createDirectories(oldFile.getParent());
        Files.createFile(oldFile);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(13L))).thenReturn(List.of(book));
        AppSettings appSettings = new AppSettings();
        appSettings.setUploadPattern("Moved/{title}");
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        FileMoveRequest request = new FileMoveRequest();
        request.setBookIds(Set.of(13L));

        fileMoveService.moveFiles(request);

        Path expectedNewPath = tempLibraryRoot.resolve("Moved").resolve(".epub");
        assertThat(Files.exists(expectedNewPath))
                .withFailMessage("File should not be moved if metadata is null")
                .isFalse();

        Files.deleteIfExists(oldFile);
    }

    @Test
    void testMoveFiles_successfulMove() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");
        Path oldFilePath = book.getFullFilePath();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        Path newPath = tempLibraryRoot.resolve("X").resolve("Test Book.epub");
        assertThat(Files.exists(newPath)).isTrue();
        assertThat(Files.exists(oldFilePath)).isFalse();

        verify(bookRepository).save(book);
        verify(notificationService).sendMessage(eq(Topic.BOOK_METADATA_BATCH_UPDATE), anyList());
        verify(libraryService).rescanLibrary(book.getLibraryPath().getLibrary().getId());
    }

    @Test
    void testMoveFiles_usesDefaultPatternWhenLibraryPatternEmpty() throws IOException {
        LibraryEntity lib = LibraryEntity.builder()
                .id(99L).name("Lib").fileNamingPattern("   ").build();
        LibraryPathEntity lp = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString()).library(lib).build();

        BookMetadataEntity meta = BookMetadataEntity.builder()
                .title("DFT").build();
        BookEntity book = BookEntity.builder()
                .id(55L).fileSubPath("old").fileName("a.epub")
                .libraryPath(lp).metadata(meta).build();

        Path old = book.getFullFilePath();
        Files.createDirectories(old.getParent());
        Files.createFile(old);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(55L))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("DEF/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(55L));
        fileMoveService.moveFiles(req);

        Path moved = tempLibraryRoot.resolve("DEF").resolve("DFT.epub");
        assertThat(Files.exists(moved)).isTrue();
        verify(bookRepository).save(book);
    }

    @Test
    void testMoveFiles_deletesEmptyParentDirectories() throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(100L).name("Lib").fileNamingPattern(null).build();
        LibraryPathEntity lp = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString()).library(library).build();
        BookMetadataEntity meta = BookMetadataEntity.builder()
                .title("Nested").build();
        BookEntity book = BookEntity.builder()
                .id(101L).fileSubPath("a/b/c").fileName("nested.epub")
                .libraryPath(lp).metadata(meta).build();
        Path old = book.getFullFilePath();
        Files.createDirectories(old.getParent());
        Files.createFile(old);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(101L))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("Z/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(bookMapper.toBook(book)).thenReturn(Book.builder().id(book.getId()).build());

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(101L));
        fileMoveService.moveFiles(req);

        Path newPath = tempLibraryRoot.resolve("Z").resolve("Nested.epub");
        assertThat(Files.exists(newPath)).isTrue();
        assertThat(Files.notExists(tempLibraryRoot.resolve("a"))).isTrue();
    }

    @Test
    void testMoveFiles_overwritesExistingDestination() throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(102L).name("Lib").fileNamingPattern(null).build();
        LibraryPathEntity lp = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString()).library(library).build();
        BookMetadataEntity meta = BookMetadataEntity.builder()
                .title("Overwrite").build();
        BookEntity book = BookEntity.builder()
                .id(103L).fileSubPath("sub").fileName("file.epub")
                .libraryPath(lp).metadata(meta).build();
        Path old = book.getFullFilePath();
        Files.createDirectories(old.getParent());
        Files.write(old, "SRC".getBytes());
        Path destDir = tempLibraryRoot.resolve("DEST");
        Files.createDirectories(destDir);
        Path dest = destDir.resolve("Overwrite.epub");
        Files.write(dest, "OLD".getBytes());

        when(bookQueryService.findAllWithMetadataByIds(Set.of(103L))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("DEST/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(bookMapper.toBook(book)).thenReturn(Book.builder().id(book.getId()).build());

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(103L));
        fileMoveService.moveFiles(req);

        assertThat(Files.exists(dest)).isTrue();
        String content = Files.readString(dest);
        assertThat(content).isEqualTo("SRC");
    }

    @Test
    void testMoveFiles_usesLibraryPatternWhenSet() throws IOException {
        LibraryEntity library = LibraryEntity.builder()
                .id(104L).name("Lib").fileNamingPattern("LIBY/{title}").build();
        LibraryPathEntity lp = LibraryPathEntity.builder()
                .path(tempLibraryRoot.toString()).library(library).build();
        BookMetadataEntity meta = BookMetadataEntity.builder()
                .title("LibTest").build();
        BookEntity book = BookEntity.builder()
                .id(105L).fileSubPath("x").fileName("file.epub")
                .libraryPath(lp).metadata(meta).build();
        Path old = book.getFullFilePath();
        Files.createDirectories(old.getParent());
        Files.createFile(old);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(105L))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("DEFAULT/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        when(bookMapper.toBook(book)).thenReturn(Book.builder().id(book.getId()).build());

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(105L));
        fileMoveService.moveFiles(req);

        Path newPath = tempLibraryRoot.resolve("LIBY").resolve("LibTest.epub");
        assertThat(Files.exists(newPath)).isTrue();
    }

    @Test
    void testMoveFiles_skipsWhenDestinationSameAsSource() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");
        Path oldFilePath = book.getFullFilePath();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId())))
                .thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("sub/mybook.epub");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        assertThat(Files.exists(oldFilePath)).isTrue();
        verify(bookRepository, never()).save(any());
        verify(notificationService, never()).sendMessage(any(), anyList());
        verify(libraryService, never()).rescanLibrary(anyLong());
    }

    @Test
    void testMoveFiles_doesNotPauseMonitoringWhenAlreadyPaused() {
        when(monitoringService.isPaused()).thenReturn(true);
        when(bookQueryService.findAllWithMetadataByIds(anySet())).thenReturn(List.of());
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(999L));
        fileMoveService.moveFiles(req);

        verify(monitoringService, never()).pauseMonitoring();
        verify(monitoringService, never()).resumeMonitoring();
    }

    @Test
    void testMoveFiles_movesAdditionalFiles() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");

        // Create additional files
        BookAdditionalFileEntity additionalFile1 = createAdditionalFile(book, 1L, "mybook.pdf", "sub", 
                AdditionalFileType.ALTERNATIVE_FORMAT, true);
        BookAdditionalFileEntity additionalFile2 = createAdditionalFile(book, 2L, "mybook_notes.txt", "sub",
                AdditionalFileType.SUPPLEMENTARY, true);

        List<BookAdditionalFileEntity> additionalFiles = List.of(additionalFile1, additionalFile2);
        book.setAdditionalFiles(additionalFiles);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify main book moved
        Path newBookPath = tempLibraryRoot.resolve("X").resolve("Test Book.epub");
        assertThat(Files.exists(newBookPath)).isTrue();

        // Verify additional files moved
        Path newAdditionalPath1 = tempLibraryRoot.resolve("X").resolve("Test Book.pdf");
        Path newAdditionalPath2 = tempLibraryRoot.resolve("X").resolve("Test Book.txt");
        assertThat(Files.exists(newAdditionalPath1)).isTrue();
        assertThat(Files.exists(newAdditionalPath2)).isTrue();

        // Verify database updated
        verify(bookAdditionalFileRepository, times(2)).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_handlesUniqueNamesForAdditionalFiles() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");

        // Create additional files that will result in same name after pattern resolution
        BookAdditionalFileEntity additionalFile1 = createAdditionalFile(book, 1L, "version1.pdf", "sub",
                AdditionalFileType.ALTERNATIVE_FORMAT, true);
        BookAdditionalFileEntity additionalFile2 = createAdditionalFile(book, 2L, "version2.pdf", "sub",
                AdditionalFileType.ALTERNATIVE_FORMAT, true);

        List<BookAdditionalFileEntity> additionalFiles = List.of(additionalFile1, additionalFile2);
        book.setAdditionalFiles(additionalFiles);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify files moved with unique names
        Path newAdditionalPath1 = tempLibraryRoot.resolve("X").resolve("Test Book.pdf");
        Path newAdditionalPath2 = tempLibraryRoot.resolve("X").resolve("Test Book_2.pdf");
        assertThat(Files.exists(newAdditionalPath1)).isTrue();
        assertThat(Files.exists(newAdditionalPath2)).isTrue();

        verify(bookAdditionalFileRepository, times(2)).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_skipsNonexistentAdditionalFiles() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");

        // Create additional file entity without actual file
        BookAdditionalFileEntity additionalFile = createAdditionalFile(book, 1L, "nonexistent.pdf", "sub",
                AdditionalFileType.ALTERNATIVE_FORMAT, false);

        book.setAdditionalFiles(List.of(additionalFile));

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify main book moved
        Path newBookPath = tempLibraryRoot.resolve("X").resolve("Test Book.epub");
        assertThat(Files.exists(newBookPath)).isTrue();

        // Verify nonexistent additional file not saved
        verify(bookAdditionalFileRepository, never()).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_handlesEmptyAdditionalFilesList() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");
        book.setAdditionalFiles(new ArrayList<>());

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify main book moved
        Path newBookPath = tempLibraryRoot.resolve("X").resolve("Test Book.epub");
        assertThat(Files.exists(newBookPath)).isTrue();

        // Verify no additional file operations
        verify(bookAdditionalFileRepository, never()).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_handlesNullAdditionalFilesList() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");
        book.setAdditionalFiles(null);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify main book moved
        Path newBookPath = tempLibraryRoot.resolve("X").resolve("Test Book.epub");
        assertThat(Files.exists(newBookPath)).isTrue();

        // Verify no additional file operations
        verify(bookAdditionalFileRepository, never()).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_additionalFileWithDifferentExtensions() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "sub", "mybook.epub");

        // Create additional files with different extensions
        BookAdditionalFileEntity additionalFile1 = createAdditionalFile(book, 1L, "mybook.pdf", "sub",
                AdditionalFileType.ALTERNATIVE_FORMAT, true);
        BookAdditionalFileEntity additionalFile2 = createAdditionalFile(book, 2L, "mybook.mobi", "sub",
                AdditionalFileType.ALTERNATIVE_FORMAT, true);
        BookAdditionalFileEntity additionalFile3 = createAdditionalFile(book, 3L, "cover.jpg", "sub",
                AdditionalFileType.SUPPLEMENTARY, true);

        List<BookAdditionalFileEntity> additionalFiles = List.of(additionalFile1, additionalFile2, additionalFile3);
        book.setAdditionalFiles(additionalFiles);

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("Library/{authors}/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);
        Book dto = Book.builder().id(book.getId()).build();
        when(bookMapper.toBook(book)).thenReturn(dto);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify all files moved with correct extensions
        Path basePath = tempLibraryRoot.resolve("Library").resolve("Author Name");
        assertThat(Files.exists(basePath.resolve("Test Book.epub"))).isTrue();
        assertThat(Files.exists(basePath.resolve("Test Book.pdf"))).isTrue();
        assertThat(Files.exists(basePath.resolve("Test Book.mobi"))).isTrue();
        assertThat(Files.exists(basePath.resolve("Test Book.jpg"))).isTrue();

        verify(bookAdditionalFileRepository, times(3)).save(any(BookAdditionalFileEntity.class));
    }

    @Test
    void testMoveFiles_skipsSamePathAdditionalFiles() throws IOException {
        BookEntity book = createBookWithFile(tempLibraryRoot, "X", "Test Book.epub");

        // Create additional file that will resolve to same path
        BookAdditionalFileEntity additionalFile = createAdditionalFile(book, 1L, "Test Book.pdf", "X",
                AdditionalFileType.ALTERNATIVE_FORMAT, true);

        book.setAdditionalFiles(List.of(additionalFile));

        when(bookQueryService.findAllWithMetadataByIds(Set.of(book.getId()))).thenReturn(List.of(book));
        AppSettings settings = new AppSettings();
        settings.setUploadPattern("X/{title}");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        FileMoveRequest req = new FileMoveRequest();
        req.setBookIds(Set.of(book.getId()));
        fileMoveService.moveFiles(req);

        // Verify files still exist at original location
        Path additionalFilePath = tempLibraryRoot.resolve("X").resolve("Test Book.pdf");
        assertThat(Files.exists(additionalFilePath)).isTrue();

        // Verify no save called for additional file (skipped)
        verify(bookAdditionalFileRepository, never()).save(any(BookAdditionalFileEntity.class));
    }
}
