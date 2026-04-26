package org.grimmory.service.file;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Mock private org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @Mock private SidecarMetadataWriter sidecarMetadataWriter;

    private FileMoveService service;
    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    static class TestableFileMoveService extends FileMoveService {
        TestableFileMoveService(AppProperties appProperties, BookRepository bookRepository, BookAdditionalFileRepository bookFileRepository,
                                LibraryRepository libraryRepository, FileMoveHelper fileMoveHelper,
                                MonitoringRegistrationService monitoringRegistrationService, LibraryMapper libraryMapper,
                                BookMapper bookMapper, NotificationService notificationService, EntityManager entityManager,
                                org.springframework.transaction.support.TransactionTemplate transactionTemplate,
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
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = invocation.getArgument(0);
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
        book.setBookFiles(new ArrayList<>(files));
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
            bookWithEmptyFiles.setBookFiles(new ArrayList<>());

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

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.bulkMoveFiles(request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("File move operations are only supported on local storage");
        }
    }
}
