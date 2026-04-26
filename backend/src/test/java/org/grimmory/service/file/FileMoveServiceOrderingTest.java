package org.grimmory.service.file;

import jakarta.persistence.EntityManager;
import org.grimmory.config.AppProperties;
import org.grimmory.mapper.BookMapper;
import org.grimmory.mapper.LibraryMapper;
import org.grimmory.model.dto.FileMoveResult;
import org.grimmory.model.dto.Library;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.entity.LibraryPathEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.repository.BookAdditionalFileRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.LibraryRepository;
import org.grimmory.service.NotificationService;
import org.grimmory.service.metadata.sidecar.SidecarMetadataWriter;
import org.grimmory.service.monitoring.MonitoringRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileMoveServiceOrderingTest {

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
                                TransactionTemplate transactionTemplate, SidecarMetadataWriter sidecarMetadataWriter) {
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
        
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
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

    private BookEntity createBook() {
        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setLibraryPath(libraryPath);

        BookFileEntity file = new BookFileEntity();
        file.setId(1L);
        file.setBook(book);
        file.setFileName("Book.epub");
        file.setFileSubPath("path");
        file.setBookFormat(true);
        file.setBookType(BookFileType.EPUB);

        book.setBookFiles(new ArrayList<>(List.of(file)));
        return book;
    }

    private void mockStandardBehavior(BookEntity book, Path targetPath) throws IOException {
        when(fileMoveHelper.getFileNamingPattern(any())).thenReturn("{title}");
        when(fileMoveHelper.generateNewFilePath(eq(book), any(LibraryPathEntity.class), anyString())).thenReturn(targetPath);
        when(fileMoveHelper.generateNewFilePath(eq(book), any(BookFileEntity.class), any(LibraryPathEntity.class), anyString()))
                .thenReturn(targetPath);
        when(fileMoveHelper.extractSubPath(any(), any())).thenReturn("new/path");
        when(fileMoveHelper.validateSourceExists(any(), anyBoolean())).thenReturn(true);
        when(fileMoveHelper.moveFileWithBackup(any())).thenAnswer(inv -> ((Path) inv.getArgument(0)).resolveSibling("temp"));
        doNothing().when(fileMoveHelper).commitMove(any(), any());
        doNothing().when(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
    }

    @Test
    @DisplayName("moveSingleFile: commits files before updating database")
    void commitsBeforeDatabaseUpdate() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        FileMoveResult result = service.moveSingleFile(book);

        assertThat(result.isMoved()).isTrue();

        InOrder inOrder = inOrder(fileMoveHelper, bookFileRepository);
        inOrder.verify(fileMoveHelper).moveFileWithBackup(any());
        inOrder.verify(fileMoveHelper).commitMove(any(), any());
        inOrder.verify(bookFileRepository).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("moveSingleFile: validates sources before staging")
    void validatesBeforeStaging() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        service.moveSingleFile(book);

        InOrder inOrder = inOrder(fileMoveHelper);
        inOrder.verify(fileMoveHelper).validateSourceExists(any(), anyBoolean());
        inOrder.verify(fileMoveHelper).moveFileWithBackup(any());
    }

    @Test
    @DisplayName("moveSingleFile: when monitored - unregisters before move, registers after")
    void monitoredOrderUnregisterMoveRegister() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(Paths.get("/library")));
        when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
        Library dto = Library.builder().build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(dto);

        FileMoveResult result = service.moveSingleFile(book);

        assertThat(result.isMoved()).isTrue();

        InOrder inOrder = inOrder(fileMoveHelper, sidecarMetadataWriter, monitoringRegistrationService, service);
        inOrder.verify(fileMoveHelper).unregisterLibrary(1L);
        inOrder.verify(monitoringRegistrationService).waitForEventsDrainedByPaths(anySet(), anyLong());
        inOrder.verify(fileMoveHelper).moveFileWithBackup(any());
        inOrder.verify(fileMoveHelper).commitMove(any(), any());
        inOrder.verify(sidecarMetadataWriter).moveSidecarFiles(any(), any());
        inOrder.verify(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
        inOrder.verify(service).sleep(anyLong());
        inOrder.verify(monitoringRegistrationService).registerLibrary(dto);
    }

    @Test
    @DisplayName("moveSingleFile: when not monitored - no monitoring calls")
    void notMonitoredSkipsMonitoringCalls() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        FileMoveResult result = service.moveSingleFile(book);

        assertThat(result.isMoved()).isTrue();
        verify(fileMoveHelper, never()).unregisterLibrary(anyLong());
        verify(monitoringRegistrationService, never()).registerLibrary(any());
        verify(service, never()).sleep(anyLong());
    }

    @Test
    @DisplayName("moveSingleFile: cleans up directories after database update")
    void cleansUpAfterDatabaseUpdate() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        service.moveSingleFile(book);

        InOrder inOrder = inOrder(bookFileRepository, sidecarMetadataWriter, fileMoveHelper);
        inOrder.verify(bookFileRepository).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
        inOrder.verify(sidecarMetadataWriter).moveSidecarFiles(any(), any());
        inOrder.verify(fileMoveHelper).deleteEmptyParentDirsUpToLibraryFolders(any(), anySet());
    }

    @Test
    @DisplayName("moveSingleFile: all files staged before any commits")
    void allFilesStagedBeforeCommits() throws IOException {
        BookEntity book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);
        book.setLibraryPath(libraryPath);

        BookFileEntity file1 = new BookFileEntity();
        file1.setId(1L);
        file1.setBook(book);
        file1.setFileName("Book.epub");
        file1.setFileSubPath("path");
        file1.setBookFormat(true);

        BookFileEntity file2 = new BookFileEntity();
        file2.setId(2L);
        file2.setBook(book);
        file2.setFileName("Book.pdf");
        file2.setFileSubPath("path");
        file2.setBookFormat(true);
        file2.setBookType(BookFileType.PDF);

        book.setBookFiles(new ArrayList<>(List.of(file1, file2)));

        Path target = Paths.get("/library/new/path/NewName.epub");
        mockStandardBehavior(book, target);
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        service.moveSingleFile(book);

        InOrder inOrder = inOrder(fileMoveHelper);
        // Both files staged first
        inOrder.verify(fileMoveHelper, times(2)).moveFileWithBackup(any());
        // Then both committed
        inOrder.verify(fileMoveHelper, times(2)).commitMove(any(), any());
    }

    @Test
    @DisplayName("moveSingleFile: rollback happens on commit failure")
    void rollbackOnCommitFailure() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        service.moveSingleFile(book);

        InOrder inOrder = inOrder(fileMoveHelper);
        inOrder.verify(fileMoveHelper).moveFileWithBackup(any());
        inOrder.verify(fileMoveHelper).commitMove(any(), any());
        inOrder.verify(fileMoveHelper).rollbackMove(any(), any());
    }

    @Test
    @DisplayName("moveSingleFile: database not updated when commit fails")
    void noDatabaseUpdateOnCommitFailure() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        service.moveSingleFile(book);

        verify(bookFileRepository, never()).updateFileNameAndSubPath(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("moveSingleFile: library re-registered even on failure")
    void libraryReRegisteredOnFailure() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        doThrow(new IOException("fail")).when(fileMoveHelper).commitMove(any(), any());
        when(monitoringRegistrationService.isLibraryMonitored(1L)).thenReturn(true);
        when(monitoringRegistrationService.getPathsForLibraries(Set.of(1L))).thenReturn(Set.of(Paths.get("/library")));
        when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
        Library dto = Library.builder().build();
        when(libraryMapper.toLibrary(any(LibraryEntity.class))).thenReturn(dto);

        service.moveSingleFile(book);

        verify(fileMoveHelper).unregisterLibrary(1L);
        verify(monitoringRegistrationService).registerLibrary(dto);
    }
    @Test
    @DisplayName("moveSingleFile: rolls back committed files on database failure")
    void filesRolledBackOnDbFailure() throws IOException {
        BookEntity book = createBook();
        Path target = Paths.get("/library/new/path/NewName.epub");

        mockStandardBehavior(book, target);
        
        // Mock DB failure
        doThrow(new RuntimeException("DB Error")).when(transactionTemplate).executeWithoutResult(any());
        
        when(monitoringRegistrationService.isLibraryMonitored(anyLong())).thenReturn(false);
        when(monitoringRegistrationService.getPathsForLibraries(anySet())).thenReturn(Collections.emptySet());

        try {
            service.moveSingleFile(book);
        } catch (RuntimeException e) {
            // Expected
        }

        InOrder inOrder = inOrder(fileMoveHelper);
        inOrder.verify(fileMoveHelper).moveFileWithBackup(any());
        inOrder.verify(fileMoveHelper).commitMove(any(), any()); // Committed
        
        // Verify manual rollback (Target -> Source)
        inOrder.verify(fileMoveHelper).moveFile(any(), any());
    }
}
