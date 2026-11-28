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
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Severity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.springframework.transaction.TransactionStatus;
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

    private FileMoveService service;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    static class TestableFileMoveService extends FileMoveService {
        TestableFileMoveService(AppProperties appProperties, BookRepository bookRepository, BookAdditionalFileRepository bookFileRepository,
                                LibraryRepository libraryRepository, FileMoveHelper fileMoveHelper,
                                MonitoringRegistrationService monitoringRegistrationService, LibraryMapper libraryMapper,
                                BookMapper bookMapper, NotificationService notificationService, EntityManager entityManager,
                                TransactionTemplate transactionTemplate,
                                SidecarMetadataWriter sidecarMetadataWriter) {
            super(appProperties, bookRepository, bookFileRepository, libraryRepository, fileMoveHelper,
                    monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager, transactionTemplate, sidecarMetadataWriter);
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
                fileMoveHelper, monitoringRegistrationService, libraryMapper, bookMapper, notificationService, entityManager, transactionTemplate, sidecarMetadataWriter));

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

    /** Mirrors SidecarMetadataWriter's naming: base name + ".metadata.json" / ".cover.jpg". */
    private void mockSidecarPaths() {
        when(sidecarMetadataWriter.getSidecarPath(any())).thenAnswer(inv -> companion(inv.getArgument(0), ".metadata.json"));
        when(sidecarMetadataWriter.getCoverPath(any())).thenAnswer(inv -> companion(inv.getArgument(0), ".cover.jpg"));
    }

    private static Path companion(Path bookPath, String suffix) {
        String name = bookPath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return bookPath.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + suffix);
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
            // Should attempt to rollback the remaining uncommitted file
            verify(fileMoveHelper, atLeastOnce()).rollbackMove(any(), any());
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
        @DisplayName("does not move or update a book whose target is occupied by another file (#3013)")
        void refusesToOverwriteExistingTarget() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            doThrow(new FileAlreadyExistsException(target.toString()))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper, never()).moveFileWithBackup(any());
            verify(fileMoveHelper, never()).commitMove(any(), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
            verify(bookRepository, never()).updateLibrary(anyLong(), anyLong(), any());
            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getSeverity() == Severity.WARN
                    && n.getMessage().equals("Not moved: \"new/NewName.epub\" is already taken by another file.")));
            // Once to resolve the target, once to re-register it afterwards: the conflict handler must not
            // query again (a failure there would escape and abort the rest of the bulk move).
            verify(libraryRepository, times(2)).findByIdWithPaths(2L);
        }

        @Test
        @DisplayName("bulk move keeps a tracked cover tracked and reserves the renamed book's cover slot")
        void bulkMoveReservesSlotForTrackedCover() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookFileEntity cover = createBookFile(2L, "Book.cover.jpg", "path", false, false);
            BookEntity book = createBook(List.of(epub, cover));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            mockSidecarPaths();

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            verify(fileMoveHelper).ensureTargetsAvailable(
                    argThat(map -> Paths.get("/target/new/Book.cover.jpg").equals(map.get(Paths.get("/library/path/Book.cover.jpg")))
                            && Paths.get("/target/new/NewName.metadata.json").equals(map.get(Paths.get("/library/path/Book.metadata.json")))),
                    eq(Set.of(Paths.get("/target/new/NewName.cover.jpg"))));
            verify(fileMoveHelper, times(1)).moveFileWithBackup(Paths.get("/library/path/Book.cover.jpg"));
            verify(fileMoveHelper, never()).commitMove(any(), eq(Paths.get("/target/new/NewName.cover.jpg")));
        }

        @Test
        @DisplayName("never folds a leftover temp file warning into the bulk summary")
        void staleTempWarningSurvivesBulkSummary() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            doThrow(new FileAlreadyExistsException("/library/path/Book.epub.tmp_move"))
                    .doThrow(new FileAlreadyExistsException(target.toString()))
                    .doThrow(new FileAlreadyExistsException(target.toString()))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveRequest request = new FileMoveRequest();
            List<FileMoveRequest.Move> moves = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                FileMoveRequest.Move move = new FileMoveRequest.Move();
                move.setBookId(100L);
                move.setTargetLibraryId(2L);
                move.setTargetLibraryPathId(20L);
                moves.add(move);
            }
            request.setMoves(moves);

            service.bulkMoveFiles(request);

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getSeverity() == Severity.WARN
                    && n.getMessage().equals("Not moved: leftover temporary file \"/library/path/Book.epub.tmp_move\" from an earlier failed move. "
                            + "Check it before deleting it: it may be the only copy of the file.")));
            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getMessage().equals("2 books were not moved: their target paths are already taken by other files.")));
            verify(notificationService, times(2)).sendMessage(eq(Topic.LOG), any());
        }

        @Test
        @DisplayName("a single path conflict next to a leftover temp file keeps its specific message")
        void singleTakenPlusStaleTempKeepsSpecificMessage() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            doThrow(new FileAlreadyExistsException(target.toString()))
                    .doThrow(new FileAlreadyExistsException("/library/path/Book.epub.tmp_move"))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveRequest request = new FileMoveRequest();
            List<FileMoveRequest.Move> moves = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                FileMoveRequest.Move move = new FileMoveRequest.Move();
                move.setBookId(100L);
                move.setTargetLibraryId(2L);
                move.setTargetLibraryPathId(20L);
                moves.add(move);
            }
            request.setMoves(moves);

            service.bulkMoveFiles(request);

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getMessage().equals("Not moved: \"new/NewName.epub\" is already taken by another file.")));
            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getMessage().startsWith("Not moved: leftover temporary file")));
            verify(notificationService, times(2)).sendMessage(eq(Topic.LOG), any());
        }

        @Test
        @DisplayName("still reports refused books when re-registering a library fails")
        void reportsConflictsEvenIfReRegistrationFails() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            doThrow(new FileAlreadyExistsException(target.toString()))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());
            // First lookup resolves the target for the move; the re-registration lookup then fails
            doReturn(Optional.of(targetLibrary)).doThrow(new RuntimeException("db down"))
                    .when(libraryRepository).findByIdWithPaths(2L);

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            Assertions.assertThatThrownBy(() -> service.bulkMoveFiles(request)).hasMessage("db down");

            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getMessage().equals("Not moved: \"new/NewName.epub\" is already taken by another file.")));
        }

        @Test
        @DisplayName("sends a single summary notification when several books conflict (#3013)")
        void summarizesMultipleConflicts() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "path", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/target/new/NewName.epub");

            mockBulkMoveSetup(book, target);
            doThrow(new FileAlreadyExistsException(target.toString()))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveRequest request = new FileMoveRequest();
            List<FileMoveRequest.Move> moves = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                FileMoveRequest.Move move = new FileMoveRequest.Move();
                move.setBookId(100L);
                move.setTargetLibraryId(2L);
                move.setTargetLibraryPathId(20L);
                moves.add(move);
            }
            request.setMoves(moves);

            service.bulkMoveFiles(request);

            verify(notificationService, times(1)).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getSeverity() == Severity.WARN
                    && n.getMessage().startsWith("3 books were not moved")));
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
            mockSidecarPaths();

            FileMoveRequest request = new FileMoveRequest();
            FileMoveRequest.Move move = new FileMoveRequest.Move();
            move.setBookId(100L);
            move.setTargetLibraryId(2L);
            move.setTargetLibraryPathId(20L);
            request.setMoves(List.of(move));

            service.bulkMoveFiles(request);

            InOrder inOrder = inOrder(fileMoveHelper);
            inOrder.verify(fileMoveHelper).commitMove(any(), eq(Paths.get("/target/new/NewName.metadata.json")));
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

    @Nested
    @DisplayName("moveSingleFile - Overwrite Protection (#3013)")
    class OverwriteProtection {

        @Test
        @DisplayName("passes every planned source -> target pair to the pre-flight check")
        void checksAllTargetsBeforeStaging() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookFileEntity cover = createBookFile(2L, "cover.jpg", "old", false, false);
            BookEntity book = createBook(List.of(epub, cover));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            InOrder inOrder = inOrder(fileMoveHelper);
            inOrder.verify(fileMoveHelper).ensureTargetsAvailable(argThat(map -> map.size() == 2
                    && map.get(Paths.get("/library/old/Book.epub")).equals(Paths.get("/library/new/path/NewName.epub"))
                    && map.get(Paths.get("/library/old/cover.jpg")).equals(Paths.get("/library/new/path/cover.jpg"))), anySet());
            inOrder.verify(fileMoveHelper, times(2)).moveFileWithBackup(any());
        }

        @Test
        @DisplayName("leaves files and database untouched when the target is occupied by another book")
        void doesNothingWhenTargetOccupied() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockMonitoredLibrary();
            doThrow(new FileAlreadyExistsException(target.toString()))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
            verify(fileMoveHelper, never()).commitMove(any(), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
            verify(monitoringRegistrationService).registerLibrary(any());
            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getSeverity() == Severity.WARN
                    && n.getMessage().equals("Not moved: \"new/path/NewName.epub\" is already taken by another file.")));
        }

        @Test
        @DisplayName("reports a leftover .tmp_move as a stale temporary file, not as a taken path")
        void reportsStaleTempDistinctly() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();
            doThrow(new FileAlreadyExistsException("/library/old/Book.epub.tmp_move"))
                    .when(fileMoveHelper).ensureTargetsAvailable(anyMap(), anySet());

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            verify(fileMoveHelper, never()).moveFileWithBackup(any());
            verify(notificationService).sendMessage(eq(Topic.LOG), argThat((Object o) -> o instanceof LogNotification n
                    && n.getSeverity() == Severity.WARN
                    && n.getMessage().equals("Not moved: leftover temporary file \"old/Book.epub.tmp_move\" from an earlier failed move. "
                            + "Check it before deleting it: it may be the only copy of the file.")));
        }

        @Test
        @DisplayName("does not send a conflict notification for unrelated failures")
        void noConflictNotificationForOtherFailures() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();
            doThrow(new IOException("disk on fire")).when(fileMoveHelper).commitMove(any(), any());

            service.moveSingleFile(book);

            verify(notificationService, never()).sendMessage(eq(Topic.LOG), any());
        }

        @Test
        @DisplayName("reverts already-committed files when a later commit fails")
        void revertsCommittedFilesOnPartialCommitFailure() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookFileEntity pdf = createBookFile(2L, "Book.pdf", "old", true, false);
            BookEntity book = createBook(List.of(epub, pdf));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockUnmonitoredLibrary();
            doAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling(((Path) inv.getArgument(0)).getFileName() + ".tmp_move"))
                    .when(fileMoveHelper).moveFileWithBackup(any());

            List<Path[]> commits = new ArrayList<>();
            doAnswer(inv -> {
                commits.add(new Path[]{inv.getArgument(0), inv.getArgument(1)});
                if (commits.size() == 2) {
                    throw new FileAlreadyExistsException(inv.getArgument(1).toString());
                }
                return null;
            }).when(fileMoveHelper).commitMove(any(), any());

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isFalse();
            assertThat(commits).hasSize(2);
            Path committedTarget = commits.get(0)[1];
            Path committedSource = Paths.get("/library/old").resolve(commits.get(0)[0].getFileName().toString().replace(".tmp_move", ""));
            Path failedTemp = commits.get(1)[0];
            Path failedSource = Paths.get("/library/old").resolve(failedTemp.getFileName().toString().replace(".tmp_move", ""));

            verify(fileMoveHelper).moveFile(committedTarget, committedSource);
            verify(fileMoveHelper).rollbackMove(failedTemp, failedSource);
            verify(fileMoveHelper, never()).rollbackMove(eq(commits.get(0)[0]), any());
            verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("moveSingleFile - Sidecars travel with the book (#3013)")
    class SidecarMoves {

        @Test
        @DisplayName("existing sidecar files are checked, staged and committed with the book file")
        void sidecarsArePartOfThePlan() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockSidecarPaths();
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper).ensureTargetsAvailable(argThat(map -> map.size() == 3
                    && Paths.get("/library/new/path/NewName.metadata.json").equals(map.get(Paths.get("/library/old/Book.metadata.json")))
                    && Paths.get("/library/new/path/NewName.cover.jpg").equals(map.get(Paths.get("/library/old/Book.cover.jpg")))), anySet());
            verify(fileMoveHelper).moveFileWithBackup(Paths.get("/library/old/Book.metadata.json"));
            verify(fileMoveHelper).moveFileWithBackup(Paths.get("/library/old/Book.cover.jpg"));
            verify(fileMoveHelper).commitMove(any(), eq(Paths.get("/library/new/path/NewName.metadata.json")));
            verify(fileMoveHelper).commitMove(any(), eq(Paths.get("/library/new/path/NewName.cover.jpg")));
        }

        @Test
        @DisplayName("sidecar target slots are claimed even when the book has no sidecar yet")
        void absentSidecarStillClaimsTargetSlot() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookEntity book = createBook(List.of(epub));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockSidecarPaths();
            mockUnmonitoredLibrary();
            when(fileMoveHelper.validateSourceExists(argThat(p -> p != null && !p.toString().endsWith(".epub")), eq(false)))
                    .thenReturn(false);

            service.moveSingleFile(book);

            verify(fileMoveHelper).ensureTargetsAvailable(argThat(map -> map.size() == 3
                    && map.containsKey(Paths.get("/library/old/Book.metadata.json"))
                    && map.containsKey(Paths.get("/library/old/Book.cover.jpg"))), anySet());
            verify(fileMoveHelper, times(1)).moveFileWithBackup(any());
            verify(fileMoveHelper).moveFileWithBackup(Paths.get("/library/old/Book.epub"));
        }

        @Test
        @DisplayName("a tracked additional file that is also the sidecar cover is moved once, as an additional file")
        void trackedCoverIsNotPlannedTwice() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookFileEntity cover = createBookFile(2L, "Book.cover.jpg", "old", false, false);
            BookEntity book = createBook(List.of(epub, cover));
            Path target = Paths.get("/library/new/path/NewName.epub");

            mockStandardBehavior(book, target);
            mockSidecarPaths();
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper, times(1)).moveFileWithBackup(Paths.get("/library/old/Book.cover.jpg"));
            verify(fileMoveHelper).commitMove(any(), eq(Paths.get("/library/new/path/Book.cover.jpg")));
            verify(fileMoveHelper, never()).commitMove(any(), eq(Paths.get("/library/new/path/NewName.cover.jpg")));
            // The renamed book's own cover slot is still claimed, even though the tracked cover keeps its name
            verify(fileMoveHelper).ensureTargetsAvailable(anyMap(),
                    eq(Set.of(Paths.get("/library/new/path/NewName.cover.jpg"))));
        }

        @Test
        @DisplayName("a tracked cover whose path does not change stays put when the book is renamed in place")
        void trackedCoverStaysOnSameFolderRename() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookFileEntity cover = createBookFile(2L, "Book.cover.jpg", "old", false, false);
            BookEntity book = createBook(List.of(epub, cover));
            Path target = Paths.get("/library/old/NewName.epub");

            mockStandardBehavior(book, target);
            mockSidecarPaths();
            mockUnmonitoredLibrary();

            FileMoveResult result = service.moveSingleFile(book);

            assertThat(result.isMoved()).isTrue();
            verify(fileMoveHelper, never()).moveFileWithBackup(Paths.get("/library/old/Book.cover.jpg"));
            verify(fileMoveHelper, never()).commitMove(any(), eq(Paths.get("/library/old/NewName.cover.jpg")));
            verify(fileMoveHelper).ensureTargetsAvailable(
                    argThat(map -> map.size() == 2
                            && Paths.get("/library/old/NewName.epub").equals(map.get(Paths.get("/library/old/Book.epub")))
                            && Paths.get("/library/old/NewName.metadata.json").equals(map.get(Paths.get("/library/old/Book.metadata.json")))),
                    eq(Set.of(Paths.get("/library/old/NewName.cover.jpg"))));
            // The tracked cover's DB record keeps pointing at the file that is still there
            verify(bookFileRepository).updateFileNameAndSubPath(eq(2L), eq("Book.cover.jpg"), anyString());
        }

        @Test
        @DisplayName("no slot is reserved when the renamed book's sidecar slot is itself one of its tracked files")
        void trackedFileAtSlotIsNotReserved() throws IOException {
            BookFileEntity epub = createBookFile(1L, "Book.epub", "old", true, false);
            BookFileEntity cover = createBookFile(2L, "Book.cover.jpg", "old", false, false);
            BookFileEntity newCover = createBookFile(3L, "NewName.cover.jpg", "old", false, false);
            BookEntity book = createBook(List.of(epub, cover, newCover));
            Path target = Paths.get("/library/old/NewName.epub");

            mockStandardBehavior(book, target);
            mockSidecarPaths();
            mockUnmonitoredLibrary();

            service.moveSingleFile(book);

            verify(fileMoveHelper).ensureTargetsAvailable(anyMap(), eq(Set.of()));
            verify(fileMoveHelper, never()).moveFileWithBackup(Paths.get("/library/old/Book.cover.jpg"));
            verify(fileMoveHelper, never()).moveFileWithBackup(Paths.get("/library/old/NewName.cover.jpg"));
        }
    }
}
