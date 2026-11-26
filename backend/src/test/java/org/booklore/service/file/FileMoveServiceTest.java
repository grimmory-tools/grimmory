package org.booklore.service.file;

import jakarta.persistence.EntityManager;
import org.booklore.config.AppProperties;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.model.dto.FileMoveResult;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.FileMoveRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.sidecar.SidecarMetadataMapper;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileMoveServiceTest {

    @Mock private AppProperties appProperties;
    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookFileRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private FileMoveHelper fileMoveHelper;
    @Mock private MonitoringRegistrationService monitoringRegistrationService;
    @Mock private LibraryMapper libraryMapper;
    @Mock private BookMapper bookMapper;
    @Mock private NotificationService notificationService;
    @Mock private EntityManager entityManager;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private SidecarMetadataWriter sidecarMetadataWriter;
    @Mock private BookFileMutationGuard bookFileMutationGuard;

    private FileMoveService service;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    static class TestableFileMoveService extends FileMoveService {
        TestableFileMoveService(AppProperties appProperties, BookRepository bookRepository, BookAdditionalFileRepository bookFileRepository,
                                LibraryRepository libraryRepository, FileMoveHelper fileMoveHelper,
                                MonitoringRegistrationService monitoringRegistrationService, LibraryMapper libraryMapper,
                                BookMapper bookMapper, NotificationService notificationService, EntityManager entityManager,
                                TransactionTemplate transactionTemplate,
                                SidecarMetadataWriter sidecarMetadataWriter, BookFileMutationGuard bookFileMutationGuard) {
            super(appProperties, bookRepository, bookFileRepository, libraryRepository, fileMoveHelper,
                    monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager, transactionTemplate, sidecarMetadataWriter, bookFileMutationGuard);
        }

        @Override
        protected void sleep(long millis) {
            // No-op
        }
    }

    @BeforeEach
    void setUp() {
        when(appProperties.isLocalStorage()).thenReturn(true);
        
        // Mock simple execution for transaction template
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = spy(new TestableFileMoveService(appProperties, bookRepository, bookFileRepository, libraryRepository,
                fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager, transactionTemplate, sidecarMetadataWriter, bookFileMutationGuard));

        library = new LibraryEntity();
        library.setId(1L);

        libraryPath = new LibraryPathEntity();
        libraryPath.setId(10L);
        libraryPath.setPath("/library");
        libraryPath.setLibrary(library);
        library.setLibraryPaths(List.of(libraryPath));
    }

    private BookEntity createBook(List<BookFileEntity> files) {
        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setLibraryPath(libraryPath);
        files.forEach(f -> f.setBook(book));
        book.setBookFiles(Set.copyOf(files));
        return book;
    }

    @Test
    void reservedMutationRejectsMovesBeforeFilesystemOperations() {
        doThrow(new IllegalStateException("reserved")).when(bookFileMutationGuard).beforeMutation();
        Assertions.assertThatThrownBy(() -> service.moveSingleFile(createBook(List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        Assertions.assertThatThrownBy(() -> service.bulkMoveFiles(new FileMoveRequest()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        verifyNoInteractions(fileMoveHelper, bookRepository, monitoringRegistrationService);
    }

    private BookFileEntity createBookFile(Long id, String name, String subPath, boolean isBook, boolean folderBased) {
        BookFileEntity file = new BookFileEntity();
        file.setId(id);
        file.setFileName(name);
        file.setFileSubPath(subPath);
        file.setBookFormat(isBook);
        file.setFolderBased(folderBased);
        if (isBook && name.contains(".")) {
            file.setBookType(name.endsWith(".pdf") ? BookFileType.PDF : BookFileType.EPUB);
        }
        return file;
    }

    private void mockStandardBehavior(BookEntity book, Path targetPath) throws IOException {
        when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(targetPath);
        when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString()))
                .thenAnswer(inv -> {
                    BookFileEntity bf = inv.getArgument(1);
                    String ext = bf.getFileName().contains(".") ? bf.getFileName().substring(bf.getFileName().lastIndexOf('.')) : "";
                    return targetPath.getParent().resolve("NewName" + ext);
                });
        when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/subpath");
        when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
        when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));
        doNothing().when(fileMoveHelper).commitMove(any(), any());
        doNothing().when(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
    }

    private void mockUnmonitoredLibrary() {
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());
    }

    private void mockMonitoredLibrary() {
        when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(Paths.get("/library")));
        when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().build());
    }

    @Nested
    @DisplayName("moveSingleFile - Success Cases")
    class MoveSingleFileSuccess {

        @Test
        @DisplayName("moves single EPUB file and returns correct result")
        void movesSingleEpubFile() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old/path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            assertThat(result.getNewFileName()).isEqualTo("NewName.epub");
            assertThat(result.getNewFileSubPath()).isEqualTo("new/subpath");
            verify(fileMoveHelper).moveFileWithBackup(any());
            verify(fileMoveHelper).commitMove(any(), any());
            verify(bookFileRepository).updateFileNameAndSubPath(eq(1L), anyString(), eq("new/subpath"));
        }

        @Test
        @DisplayName("moves single PDF file")
        void movesSinglePdfFile() throws IOException {
            BookFileEntity pdf = createBookFile(1L, "Book.pdf", "path", true, false);
            BookEntity book = createBook(List.of(pdf));
            Path target = Paths.get("/library/new/path/NewName.pdf");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
        }

        @Test
        @DisplayName("moves multi-format book (EPUB + PDF) atomically")
        void movesMultiFormatBook() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "path", true, false);
            BookEntity book = createBook(List.of(epub, pdf));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper, times(2)).moveFileWithBackup(any());
            verify(fileMoveHelper, times(2)).commitMove(any(), any());
            verify(bookFileRepository, times(2)).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("moves book with cover image")
        void movesBookWithCover() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity cover = createBookFile(2L, "cover.jpg", "path", false, false);
            BookEntity book = createBook(List.of(epub, cover));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper, times(2)).moveFileWithBackup(any());
            verify(fileMoveHelper, times(2)).commitMove(any(), any());
        }

        @Test
        @DisplayName("moves book with multiple additional files (cover, metadata, etc)")
        void movesBookWithMultipleAdditionalFiles() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity cover = createBookFile(2L, "cover.jpg", "path", false, false);
            BookFileEntity metadata = createBookFile(3L, "metadata.opf", "path", false, false);
            BookFileEntity thumbnail = createBookFile(4L, "thumb.png", "path", false, false);
            BookEntity book = createBook(List.of(epub, cover, metadata, thumbnail));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper, times(4)).moveFileWithBackup(any());
            verify(fileMoveHelper, times(4)).commitMove(any(), any());
            verify(bookFileRepository, times(4)).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("moves folder-based audiobook")
        void movesFolderBasedAudiobook() throws IOException {
            BookFileEntity folder = createBookFile(1L, "Dr. Who Season 1", "audiobooks", true, true);
            BookEntity book = createBook(List.of(folder));
            Path target = Paths.get("/library/new/audiobooks/Dr. Who Season 1");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper).validateSourceExists(any(), eq(true));
        }

        @Test
        @DisplayName("moves folder with dots in name (e.g., 'Dr. Who')")
        void movesFolderWithDotsInName() throws IOException {
            BookFileEntity folder = createBookFile(1L, "Dr. Strange Vol. 1", "audiobooks", true, true);
            BookEntity book = createBook(List.of(folder));
            Path target = Paths.get("/library/new/audiobooks/Dr. Strange Vol. 1");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
        }

        @Test
        @DisplayName("cleans up empty parent directories after move")
        void cleansUpEmptyParentDirs() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "deep/nested/path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - No Move Required Cases")
    class MoveSingleFileNoMoveRequired {

        @Test
        @DisplayName("returns false when source and target paths are identical")
        void returnsFalseWhenPathsIdentical() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString()))
                    .thenReturn(book.getFullFilePath());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
            verify(fileMoveHelper, never()).commitMove(any(), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("returns false when book has empty file list")
        void returnsFalseWhenEmptyFileList() {
            BookEntity book = createBook(List.of());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
        }

        @Test
        @DisplayName("returns false when book has null file list")
        void returnsFalseWhenNullFileList() {
            BookEntity book = new BookEntity();
            book.setId(100L);
            book.setLibrary(library);
            book.setLibraryPath(libraryPath);
            book.setBookFiles(null);

            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.empty());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
        }

        @Test
        @DisplayName("returns false when all individual file paths are already correct")
        void returnsFalseWhenAllFilesAlreadyCorrect() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "path", true, false);
            BookEntity book = createBook(List.of(epub, pdf));

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            // Primary path differs so we enter the move logic
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString()))
                    .thenReturn(Paths.get("/library/new/path/NewName.epub"));
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("path");
            // But individual file paths are the same
            when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString()))
                    .thenAnswer(inv -> {
                        BookFileEntity bf = inv.getArgument(1);
                        return bf.getFullFilePath();
                    });
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - Validation Failure Cases")
    class MoveSingleFileValidationFailure {

        @Test
        @DisplayName("returns false when source file does not exist")
        void returnsFalseWhenSourceFileNotFound() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(target);
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/path");
            when(fileMoveHelper.validateSourceExists(any(), eq(false))).thenReturn(false);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("returns false when folder-based audiobook directory does not exist")
        void returnsFalseWhenFolderNotFound() throws IOException {
            BookFileEntity folder = createBookFile(1L, "Audiobook", "path", true, true);
            BookEntity book = createBook(List.of(folder));
            Path target = Paths.get("/library/new/path/Audiobook");

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(target);
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/path");
            when(fileMoveHelper.validateSourceExists(any(), eq(true))).thenReturn(false);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("aborts entire operation if any file in multi-file book is missing")
        void abortsIfAnyFileInMultiFileBookMissing() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "path", true, false);
            BookFileEntity cover = createBookFile(3L, "cover.jpg", "path", false, false);
            BookEntity book = createBook(List.of(epub, pdf, cover));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(target);
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/path");
            // First two files exist, third doesn't
            when(fileMoveHelper.validateSourceExists(any(), eq(false)))
                    .thenReturn(true)
                    .thenReturn(true)
                    .thenReturn(false);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("returns false when book not found in repository during lazy load")
        void returnsFalseWhenBookNotFoundInRepository() {
            BookEntity book = new BookEntity();
            book.setId(100L);
            book.setLibrary(library);
            book.setLibraryPath(libraryPath);
            book.setBookFiles(null);

            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.empty());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(bookRepository).findByIdWithBookFiles(100L);
        }

        @Test
        @DisplayName("returns false when loaded book has no files")
        void returnsFalseWhenLoadedBookHasNoFiles() {
            BookEntity bookWithoutFiles = new BookEntity();
            bookWithoutFiles.setId(100L);
            bookWithoutFiles.setLibrary(library);
            bookWithoutFiles.setLibraryPath(libraryPath);
            bookWithoutFiles.setBookFiles(null);

            BookEntity loadedBook = createBook(List.of());

            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(loadedBook));
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(bookWithoutFiles);

            assertThat(result.isMoved()).isFalse();
        }
    }

    @Nested
    @DisplayName("moveSingleFile - Failure and Rollback Cases")
    class MoveSingleFileFailureAndRollback {

        @Test
        @DisplayName("rolls back when staging fails")
        void rollsBackWhenStagingFails() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(target);
            when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString())).thenReturn(target);
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/path");
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            when(fileMoveHelper.moveFileWithBackup(any())).thenThrow(new IOException("Disk full"));
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("rolls back staged file when commit fails")
        void rollsBackWhenCommitFails() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            doThrow(new IOException("Permission denied")).when(fileMoveHelper).commitMove(any(), any());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper).rollbackMove(any(), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("rolls back all staged files when one commit fails in multi-file book")
        void rollsBackAllStagedFilesWhenOneCommitFails() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "path", true, false);
            BookEntity book = createBook(List.of(epub, pdf));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            // First commit succeeds, second fails
            doNothing().doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper).moveFile(any(), any());
            // Should attempt to rollback the remaining uncommitted file
            verify(fileMoveHelper, atLeastOnce()).rollbackMove(any(), any());
        }

        @Test
        void restoresFilesOnDiskWhenSecondCommitFails(@TempDir Path root) throws IOException {
            libraryPath.setPath(root.toString());
            Path sourceDir = Files.createDirectories(root.resolve("old"));
            Files.writeString(sourceDir.resolve("Book.epub"), "epub");
            Files.writeString(sourceDir.resolve("Book.pdf"), "pdf");
            BookEntity book = createBook(List.of(
                    createBookFile(1L, "Book.epub", "old", true, false),
                    createBookFile(2L, "Book.pdf", "old", true, false)));

            FileMoveHelper realHelper = spy(new FileMoveHelper(monitoringRegistrationService, mock(AppSettingService.class)));
            doReturn("{title}").when(realHelper).getFileNamingPattern(any());
            doReturn(root.resolve("new").resolve("NewName.epub"))
                    .when(realHelper).generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString());
            doAnswer(inv -> root.resolve("new").resolve(((BookFileEntity) inv.getArgument(1)).getFileName()))
                    .when(realHelper).generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString());
            AtomicInteger commits = new AtomicInteger();
            doAnswer(inv -> {
                if (commits.incrementAndGet() == 2) {
                    throw new IOException("second commit failed");
                }
                return inv.callRealMethod();
            }).when(realHelper).commitMove(any(), any());

            FileMoveService actualService = new TestableFileMoveService(appProperties, bookRepository, bookFileRepository,
                    libraryRepository, realHelper, monitoringRegistrationService, libraryMapper, bookMapper,
                    notificationService, entityManager, transactionTemplate, sidecarMetadataWriter, bookFileMutationGuard);
            assertThat(actualService.moveSingleFile(book).isMoved()).isFalse();
            assertThat(Files.readString(sourceDir.resolve("Book.epub"))).isEqualTo("epub");
            assertThat(Files.readString(sourceDir.resolve("Book.pdf"))).isEqualTo("pdf");
            assertThat(Files.exists(sourceDir.resolve("Book.epub.tmp_move"))).isFalse();
            assertThat(Files.exists(sourceDir.resolve("Book.pdf.tmp_move"))).isFalse();
            assertThat(Files.exists(root.resolve("new").resolve("Book.epub"))).isFalse();
            assertThat(Files.exists(root.resolve("new").resolve("Book.pdf"))).isFalse();
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        void outerTransactionDecidesFileAndSidecarMoves(@TempDir Path root) throws IOException {
            for (boolean rollback : new boolean[]{true, false}) {
                Path libraryRoot = Files.createDirectories(root.resolve(rollback ? "rollback" : "commit"));
                Path sourceDir = Files.createDirectories(libraryRoot.resolve("old"));
                Path source = sourceDir.resolve("Book.epub");
                Path target = libraryRoot.resolve("new").resolve("NewName.epub");
                Path oldSidecar = sourceDir.resolve("Book.metadata.json");
                Path newSidecar = target.getParent().resolve("NewName.metadata.json");
                Files.writeString(source, "epub");
                Files.writeString(oldSidecar, "metadata");
                libraryPath.setPath(libraryRoot.toString());
                BookEntity book = createBook(List.of(createBookFile(1L, "Book.epub", "old", true, false)));
                when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(true);
                when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(libraryRoot));
                when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
                when(libraryMapper.toLibrary(library)).thenReturn(Library.builder().build());
                clearInvocations(monitoringRegistrationService);

                DriverManagerDataSource dataSource = new DriverManagerDataSource(
                        "jdbc:h2:mem:move_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1", "sa", "");
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                jdbc.execute("CREATE TABLE file_location (id INT PRIMARY KEY, location VARCHAR(40))");
                jdbc.update("INSERT INTO file_location VALUES (1, 'old')");
                TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                doAnswer(inv -> jdbc.update("UPDATE file_location SET location = 'new' WHERE id = 1"))
                        .when(bookFileRepository).updateFileNameAndSubPath(anyLong(), anyString(), anyString());

                FileMoveHelper realHelper = spy(new FileMoveHelper(monitoringRegistrationService, mock(AppSettingService.class)));
                doReturn("{title}").when(realHelper).getFileNamingPattern(any());
                doReturn(target).when(realHelper).generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString());
                doReturn(target).when(realHelper).generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString());
                SidecarMetadataWriter realSidecar = new SidecarMetadataWriter(appProperties, mock(SidecarMetadataMapper.class),
                        mock(FileService.class), mock(AppSettingService.class));
                FileMoveService actualService = new TestableFileMoveService(appProperties, bookRepository, bookFileRepository,
                        libraryRepository, realHelper, monitoringRegistrationService, libraryMapper, bookMapper,
                        notificationService, entityManager, transactions, realSidecar, bookFileMutationGuard);

                transactions.executeWithoutResult(status -> {
                    assertThat(actualService.moveSingleFile(book).isMoved()).isTrue();
                    assertThat(Files.exists(oldSidecar)).isTrue();
                    assertThat(Files.exists(newSidecar)).isFalse();
                    verify(monitoringRegistrationService, never()).registerLibrary(any());
                    if (rollback) {
                        status.setRollbackOnly();
                    }
                });

                assertThat(jdbc.queryForObject("SELECT location FROM file_location WHERE id = 1", String.class))
                        .isEqualTo(rollback ? "old" : "new");
                assertThat(Files.exists(source)).isEqualTo(rollback);
                assertThat(Files.exists(target)).isEqualTo(!rollback);
                assertThat(Files.exists(oldSidecar)).isEqualTo(rollback);
                assertThat(Files.exists(newSidecar)).isEqualTo(!rollback);
                verify(monitoringRegistrationService).registerLibrary(any());
            }
        }

        @Test
        void innerCommittedMoveIsNotRolledBackWithOuterTransaction() throws IOException {
            BookEntity outerBook = createBook(List.of(createBookFile(1L, "outer.epub", "old", true, false)));
            BookEntity innerBook = createBook(List.of(createBookFile(2L, "inner.epub", "old", true, false)));
            innerBook.setId(101L);
            Path outerTarget = Paths.get("/library/new/outer.epub");
            Path innerTarget = Paths.get("/library/new/inner.epub");
            when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(outerBook), any(LibraryPathEntity.class), anyString())).thenReturn(outerTarget);
            when(fileMoveHelper.generateNewFilePath(eq(innerBook), any(LibraryPathEntity.class), anyString())).thenReturn(innerTarget);
            when(fileMoveHelper.generateNewFilePath(eq(outerBook), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString())).thenReturn(outerTarget);
            when(fileMoveHelper.generateNewFilePath(eq(innerBook), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString())).thenReturn(innerTarget);
            when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new");
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));

            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:h2:mem:move_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1", "sa", "");
            DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
            TransactionTemplate outer = new TransactionTemplate(manager);
            TransactionTemplate inner = new TransactionTemplate(manager);
            inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            FileMoveService actualService = new TestableFileMoveService(appProperties, bookRepository, bookFileRepository,
                    libraryRepository, fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper,
                    notificationService, entityManager, new TransactionTemplate(manager), sidecarMetadataWriter, bookFileMutationGuard);

            outer.executeWithoutResult(status -> {
                assertThat(actualService.moveSingleFile(outerBook).isMoved()).isTrue();
                inner.executeWithoutResult(innerStatus ->
                        assertThat(actualService.moveSingleFile(innerBook).isMoved()).isTrue());
                status.setRollbackOnly();
            });

            verify(fileMoveHelper).moveFile(outerTarget, outerBook.getFullFilePath());
            verify(fileMoveHelper, never()).moveFile(eq(innerTarget), any());
            verify(sidecarMetadataWriter).moveSidecarFiles(innerBook.getFullFilePath(), innerTarget);
            verify(sidecarMetadataWriter, never()).moveSidecarFiles(outerBook.getFullFilePath(), outerTarget);
        }

        @Test
        @DisplayName("database not updated when commit fails")
        void databaseNotUpdatedWhenCommitFails() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - Monitoring Cases")
    class MoveSingleFileMonitoring {

        @Test
        @DisplayName("unregisters library before move when monitored")
        void unregistersBeforeMove() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockMonitoredLibrary();

            service.moveSingleFile(book);

            verify(fileMoveHelper).unregisterLibrary(1L);
        }

        @Test
        @DisplayName("waits for events to drain after unregistering")
        void waitsForEventsDrain() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockMonitoredLibrary();

            service.moveSingleFile(book);

            verify(monitoringRegistrationService).waitForEventsDrainedByPaths(anySet(), anyLong());
        }

        @Test
        @DisplayName("re-registers library after successful move")
        void reRegistersAfterSuccess() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockMonitoredLibrary();

            service.moveSingleFile(book);

            verify(monitoringRegistrationService).registerLibrary(any());
        }

        @Test
        @DisplayName("re-registers library even after failure")
        void reRegistersAfterFailure() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
            mockMonitoredLibrary();

            service.moveSingleFile(book);

            verify(monitoringRegistrationService).registerLibrary(any());
        }

        @Test
        @DisplayName("sets watch=true before re-registering")
        void setsWatchTrueBeforeReRegistering() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(true);
            when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(Paths.get("/library")));
            when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
            Library dto = Library.builder().watch(false).build();
            when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(dto);

            service.moveSingleFile(book);

            assertThat(dto.isWatch()).isTrue();
            verify(monitoringRegistrationService).registerLibrary(dto);
        }

        @Test
        @DisplayName("skips monitoring operations when library not monitored")
        void skipsMonitoringWhenNotMonitored() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(fileMoveHelper, never()).unregisterLibrary(anyLong());
            verify(monitoringRegistrationService, never()).registerLibrary(any());
            verify(service, never()).sleep(anyLong());
        }

        @Test
        @DisplayName("treats library as monitored when has paths but status is false")
        void treatsAsMonitoredWhenHasPathsButStatusFalse() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(false);
            when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(Paths.get("/library")));
            when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
            when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().build());

            service.moveSingleFile(book);

            verify(fileMoveHelper).unregisterLibrary(1L);
            verify(monitoringRegistrationService).registerLibrary(any());
        }

        @Test
        @DisplayName("sleeps after move when monitored to allow events to drain")
        void sleepsAfterMoveWhenMonitored() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockMonitoredLibrary();

            service.moveSingleFile(book);

            verify(service).sleep(anyLong());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - Lazy Loading Cases")
    class MoveSingleFileLazyLoading {

        @Test
        @DisplayName("fetches book files from repository when null")
        void fetchesWhenNull() throws IOException {
            BookEntity bookWithoutFiles = new BookEntity();
            bookWithoutFiles.setId(100L);
            bookWithoutFiles.setLibrary(library);
            bookWithoutFiles.setLibraryPath(libraryPath);
            bookWithoutFiles.setBookFiles(null);

            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity loadedBook = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(loadedBook));
            mockStandardBehavior(loadedBook, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(bookWithoutFiles);

            assertThat(result.isMoved()).isTrue();
            verify(bookRepository).findByIdWithBookFiles(100L);
        }

        @Test
        @DisplayName("fetches book files from repository when empty list")
        void fetchesWhenEmpty() throws IOException {
            BookEntity bookWithEmptyFiles = new BookEntity();
            bookWithEmptyFiles.setId(100L);
            bookWithEmptyFiles.setLibrary(library);
            bookWithEmptyFiles.setLibraryPath(libraryPath);
            bookWithEmptyFiles.setBookFiles(new HashSet<>());

            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity loadedBook = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(loadedBook));
            mockStandardBehavior(loadedBook, target);
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(bookWithEmptyFiles);

            assertThat(result.isMoved()).isTrue();
            verify(bookRepository).findByIdWithBookFiles(100L);
        }

        @Test
        @DisplayName("does not fetch when book files already loaded")
        void doesNotFetchWhenLoaded() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(bookRepository, never()).findByIdWithBookFiles(anyLong());
        }
    }

    @Nested
    @DisplayName("bulkMoveFiles - Success Cases")
    class BulkMoveFilesSuccess {

        private LibraryEntity targetLibrary;
        private LibraryPathEntity targetLibraryPath;

        @BeforeEach
        void setUpTarget() {
            targetLibrary = new LibraryEntity();
            targetLibrary.setId(2L);

            targetLibraryPath = new LibraryPathEntity();
            targetLibraryPath.setId(20L);
            targetLibraryPath.setPath("/target");
            targetLibraryPath.setLibrary(targetLibrary);
            targetLibrary.setLibraryPaths(List.of(targetLibraryPath));
        }

        private void mockBulkMoveSetup(BookEntity book, Path targetPath) throws IOException {
            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.of(targetLibrary));

            when(fileMoveHelper.getFileNamingPattern(targetLibrary)).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), eq(targetLibraryPath), anyString())).thenReturn(targetPath);
            when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), eq(targetLibraryPath), anyString()))
                    .thenAnswer(inv -> {
                        BookFileEntity bf = inv.getArgument(1);
                        String ext = bf.getFileName().contains(".") ? bf.getFileName().substring(bf.getFileName().lastIndexOf('.')) : "";
                        return targetPath.getParent().resolve("NewName" + ext);
                    });
            when(fileMoveHelper.extractSubPath(any(), eq(targetLibraryPath))).thenReturn("new");
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));
            doNothing().when(fileMoveHelper).commitMove(any(), any());
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library"), Paths.get("/target")));
        }

        @Test
        @DisplayName("moves single book to target library")
        void movesSingleBook() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper).commitMove(any(), any());
            verify(bookRepository).updateLibrary(100L, 2L, targetLibraryPath);
            verify(notificationService).sendMessage(eq(Topic.BOOK_UPDATE), any());
        }

        @Test
        @DisplayName("moves multi-format book with all files")
        void movesMultiFormatBook() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "path", true, false);
            BookFileEntity cover = createBookFile(3L, "cover.jpg", "path", false, false);
            BookEntity book = createBook(List.of(epub, pdf, cover));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, times(3)).moveFileWithBackup(any());
            verify(fileMoveHelper, times(3)).commitMove(any(), any());
            verify(bookFileRepository, times(3)).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }

        @Test
        void restoresCommittedFileWhenLaterBulkCommitFails() throws IOException {
            BookEntity book = createBook(List.of(
                    createBookFile(1L, "Book.epub", "path", true, false),
                    createBookFile(2L, "Book.pdf", "path", true, false)));
            mockBulkMoveSetup(book, Paths.get("/target/new/NewName.epub"));
            doNothing().doThrow(new IOException("second commit failed")).when(fileMoveHelper).commitMove(any(), any());
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            FileMoveRequest request = new FileMoveRequest();
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper).moveFile(any(), any());
            verify(fileMoveHelper, atLeastOnce()).rollbackMove(any(), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
            verify(bookRepository, never()).updateLibrary(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("updates database after all file moves")
        void updatesDatabaseAfterMoves() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(bookFileRepository).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
            verify(bookRepository).updateLibrary(eq(100L), eq(2L), eq(targetLibraryPath));
        }

        @Test
        @DisplayName("sends notification after successful move")
        void sendsNotification() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(notificationService).sendMessage(eq(Topic.BOOK_UPDATE), any());
        }

        @Test
        @DisplayName("clears entity manager after move")
        void clearsEntityManager() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(entityManager).clear();
        }

        @Test
        @DisplayName("moves sidecars before cleaning up empty source directories")
        void movesSidecarsBeforeCleanup() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            InOrder inOrder = inOrder(sidecarMetadataWriter, fileMoveHelper);
            inOrder.verify(sidecarMetadataWriter).moveSidecarFiles(any(), any());
            inOrder.verify(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
        }
    }

    @Nested
    @DisplayName("bulkMoveFiles - Failure Cases")
    class BulkMoveFilesFailure {

        @BeforeEach
        void setUpMonitoring() {
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());
        }

        @Test
        @DisplayName("skips gracefully when book not found")
        void skipsWhenBookNotFound() throws IOException {
            when(bookRepository.findById(100L)).thenReturn(Optional.empty());
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.empty());

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, never()).moveFileWithBackup(any());
            verify(bookRepository, never()).updateLibrary(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("skips gracefully when target library not found")
        void skipsWhenTargetLibraryNotFound() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));

            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.empty());
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library")));

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("skips gracefully when target library path not found")
        void skipsWhenLibraryPathNotFound() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));

            LibraryEntity targetLibrary = new LibraryEntity();
            targetLibrary.setId(2L);
            LibraryPathEntity differentPath = new LibraryPathEntity();
            differentPath.setId(99L); // Different ID than requested
            differentPath.setPath("/other");
            differentPath.setLibrary(targetLibrary);
            targetLibrary.setLibraryPaths(List.of(differentPath));

            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.of(targetLibrary));
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library")));

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L); // Doesn't exist in target library
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("skips when book has no files")
        void skipsWhenBookHasNoFiles() throws IOException {
            BookEntity book = createBook(List.of());

            LibraryEntity targetLibrary = new LibraryEntity();
            targetLibrary.setId(2L);
            LibraryPathEntity targetPath = new LibraryPathEntity();
            targetPath.setId(20L);
            targetPath.setPath("/target");
            targetPath.setLibrary(targetLibrary);
            targetLibrary.setLibraryPaths(List.of(targetPath));

            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.of(targetLibrary));
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library")));

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, never()).moveFileWithBackup(any());
        }
    }

    @Nested
    @DisplayName("bulkMoveFiles - Monitoring Cases")
    class BulkMoveFilesMonitoring {

        @Test
        @DisplayName("unregisters all affected libraries before moves")
        void unregistersAllLibraries() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));

            LibraryEntity targetLibrary = new LibraryEntity();
            targetLibrary.setId(2L);
            LibraryPathEntity targetPath = new LibraryPathEntity();
            targetPath.setId(20L);
            targetPath.setPath("/target");
            targetPath.setLibrary(targetLibrary);
            targetLibrary.setLibraryPaths(List.of(targetPath));

            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.of(targetLibrary));
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library"), Paths.get("/target")));
            when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().build());

            when(fileMoveHelper.getFileNamingPattern(targetLibrary)).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), eq(targetPath), anyString())).thenReturn(Paths.get("/target/new/NewName.epub"));
            when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), eq(targetPath), anyString())).thenReturn(Paths.get("/target/new/NewName.epub"));
            when(fileMoveHelper.extractSubPath(any(), eq(targetPath))).thenReturn("new");
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));
            doNothing().when(fileMoveHelper).commitMove(any(), any());

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(monitoringRegistrationService).unregisterLibraries(anySet());
        }

        @Test
        @DisplayName("re-registers all affected libraries after moves")
        void reRegistersAllLibraries() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));

            LibraryEntity targetLibrary = new LibraryEntity();
            targetLibrary.setId(2L);
            LibraryPathEntity targetPath = new LibraryPathEntity();
            targetPath.setId(20L);
            targetPath.setPath("/target");
            targetPath.setLibrary(targetLibrary);
            targetLibrary.setLibraryPaths(List.of(targetPath));

            when(bookRepository.findById(100L)).thenReturn(Optional.of(book));
            when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
            when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
            when(libraryRepository.findByIdWithPaths(2L)).thenReturn(Optional.of(targetLibrary));
            when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Set.of(Paths.get("/library"), Paths.get("/target")));
            when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(Library.builder().build());

            when(fileMoveHelper.getFileNamingPattern(targetLibrary)).thenReturn("{title}");
            when(fileMoveHelper.generateNewFilePath(eq(book), eq(targetPath), anyString())).thenReturn(Paths.get("/target/new/NewName.epub"));
            when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), eq(targetPath), anyString())).thenReturn(Paths.get("/target/new/NewName.epub"));
            when(fileMoveHelper.extractSubPath(any(), eq(targetPath))).thenReturn("new");
            when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
            when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));
            doNothing().when(fileMoveHelper).commitMove(any(), any());

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            // Both source and target libraries should be re-registered
            verify(monitoringRegistrationService, times(2)).registerLibrary(any());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - L1 Cache Eviction")
    class MoveSingleFileL1CacheEviction {

        @Test
        @DisplayName("evicts L1-cached LibraryEntity before file move to avoid LazyInitializationException")
        void evictsCachedLibraryWhenPresentInPersistenceContext() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(entityManager.contains(library)).thenReturn(true);

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(entityManager).contains(library);
            verify(entityManager).detach(library);
        }

        @Test
        @DisplayName("skips eviction when LibraryEntity is not in persistence context")
        void skipsEvictionWhenNotInPersistenceContext() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            when(entityManager.contains(library)).thenReturn(false);

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(entityManager).contains(library);
            verify(entityManager, never()).detach(any());
        }
    }

    @Nested
    @DisplayName("NetworkStorageGating")
    class NetworkStorageGating {

        @Test
        @DisplayName("bulkMoveFiles throws IllegalStateException on network storage")
        void bulkMoveFiles_networkStorage_throwsIllegalStateException() {
            when(appProperties.isLocalStorage()).thenReturn(false);
            when(appProperties.getDiskType()).thenReturn("NETWORK");

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            Assertions.assertThatThrownBy(() -> service.bulkMoveFiles(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("File move operations are only supported on local storage");
        }
    }
}
