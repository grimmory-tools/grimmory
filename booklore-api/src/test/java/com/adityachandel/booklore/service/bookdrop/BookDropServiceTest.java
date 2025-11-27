package com.adityachandel.booklore.service.bookdrop;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.mapper.BookdropFileMapper;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookdropFile;
import com.adityachandel.booklore.model.dto.BookdropFileNotification;
import com.adityachandel.booklore.model.dto.request.BookdropFinalizeRequest;
import com.adityachandel.booklore.model.dto.response.BookdropFinalizeResult;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookdropFileEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.BookdropFileRepository;
import com.adityachandel.booklore.repository.LibraryPathRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.file.FileMovingHelper;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookDropServiceTest {

    @Mock
    private BookdropFileRepository bookdropFileRepository;
    @Mock
    private MonitoringRegistrationService monitoringRegistrationService;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private LibraryPathRepository libraryPathRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookdropMonitoringService bookdropMonitoringService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MetadataRefreshService metadataRefreshService;
    @Mock
    private BookdropNotificationService bookdropNotificationService;
    @Mock
    private BookFileProcessorRegistry processorRegistry;
    @Mock
    private AppProperties appProperties;
    @Mock
    private BookdropFileMapper mapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private FileMovingHelper fileMovingHelper;

    @InjectMocks
    private BookDropService bookDropService;

    @TempDir
    Path tempDir;

    private BookdropFileEntity bookdropFileEntity;
    private LibraryEntity libraryEntity;
    private BookdropFile bookdropFile;

    @BeforeEach
    void setUp() throws IOException {
        LibraryPathEntity libraryPathEntity = new LibraryPathEntity();
        libraryPathEntity.setId(1L);
        libraryPathEntity.setPath(tempDir.toString());

        libraryEntity = new LibraryEntity();
        libraryEntity.setId(1L);
        libraryEntity.setName("Test Library");
        libraryEntity.setLibraryPaths(List.of(libraryPathEntity));

        bookdropFileEntity = new BookdropFileEntity();
        bookdropFileEntity.setId(1L);
        bookdropFileEntity.setFileName("test-book.pdf");
        bookdropFileEntity.setFilePath(tempDir.resolve("test-book.pdf").toString());
        bookdropFileEntity.setStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        bookdropFileEntity.setOriginalMetadata("{\"title\":\"Test Book\"}");
        bookdropFileEntity.setFetchedMetadata(null);

        bookdropFile = new BookdropFile();
        bookdropFile.setId(1L);
        bookdropFile.setFileName("test-book.pdf");

        Files.createFile(tempDir.resolve("test-book.pdf"));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder()) // reverse order for directories
                      .forEach(path -> {
                          try {
                              if (!path.equals(tempDir)) { // Don't delete the tempDir itself
                                  Files.deleteIfExists(path);
                              }
                          } catch (IOException e) {
                              // Ignore cleanup failures in tearDown
                          }
                      });
            }
        }
    }

    @Test
    void getFileNotificationSummary_ShouldReturnCorrectCounts() {
        when(bookdropFileRepository.countByStatus(BookdropFileEntity.Status.PENDING_REVIEW)).thenReturn(5L);
        when(bookdropFileRepository.count()).thenReturn(10L);

        BookdropFileNotification result = bookDropService.getFileNotificationSummary();

        assertEquals(5, result.getPendingCount());
        assertEquals(10, result.getTotalCount());
        assertNotNull(result.getLastUpdatedAt());
        verify(bookdropFileRepository).countByStatus(BookdropFileEntity.Status.PENDING_REVIEW);
        verify(bookdropFileRepository).count();
    }

    @Test
    void getFilesByStatus_WhenStatusIsPending_ShouldReturnPendingFiles() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookdropFileEntity> entityPage = new PageImpl<>(List.of(bookdropFileEntity));
        Page<BookdropFile> expectedPage = new PageImpl<>(List.of(bookdropFile));

        when(bookdropFileRepository.findAllByStatus(BookdropFileEntity.Status.PENDING_REVIEW, pageable))
                .thenReturn(entityPage);
        when(mapper.toDto(bookdropFileEntity)).thenReturn(bookdropFile);

        Page<BookdropFile> result = bookDropService.getFilesByStatus("pending", pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(bookdropFile, result.getContent().getFirst());
        verify(bookdropFileRepository).findAllByStatus(BookdropFileEntity.Status.PENDING_REVIEW, pageable);
        verify(mapper).toDto(bookdropFileEntity);
    }

    @Test
    void getFilesByStatus_WhenStatusIsNotPending_ShouldReturnAllFiles() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookdropFileEntity> entityPage = new PageImpl<>(List.of(bookdropFileEntity));

        when(bookdropFileRepository.findAll(pageable)).thenReturn(entityPage);
        when(mapper.toDto(bookdropFileEntity)).thenReturn(bookdropFile);

        Page<BookdropFile> result = bookDropService.getFilesByStatus("all", pageable);

        assertEquals(1, result.getContent().size());
        verify(bookdropFileRepository).findAll(pageable);
        verify(mapper).toDto(bookdropFileEntity);
    }

    @Test
    void getBookdropCover_WhenCoverExists_ShouldReturnResource() throws IOException {
        long bookdropId = 1L;
        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());
        Path coverPath = tempDir.resolve("bookdrop_temp").resolve("1.jpg");
        Files.createDirectories(coverPath.getParent());
        Files.createFile(coverPath);

        Resource result = bookDropService.getBookdropCover(bookdropId);

        assertNotNull(result);
        assertTrue(result.exists());
    }

    @Test
    void getBookdropCover_WhenCoverDoesNotExist_ShouldReturnNull() {
        long bookdropId = 999L;
        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());

        Resource result = bookDropService.getBookdropCover(bookdropId);

        assertNull(result);
    }

    @Test
    void finalizeImport_ShouldPauseAndResumeMonitoring() {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(false);
        request.setFiles(List.of());

        BookdropFinalizeResult result = bookDropService.finalizeImport(request);

        assertNotNull(result);
        assertNotNull(result.getProcessedAt());
        verify(bookdropMonitoringService).pauseMonitoring();
        verify(bookdropMonitoringService).resumeMonitoring();
    }

    @Test
    @Disabled
    void finalizeImport_WhenSelectAllTrue_ShouldProcessAllFiles() throws Exception {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);
        request.setExcludedIds(List.of());

        BookMetadata metadata = new BookMetadata();
        metadata.setTitle("Test Book");

        when(bookdropFileRepository.findAllExcludingIdsFlat(any())).thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadata);
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(tempDir.resolve("moved-book.pdf"));

        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);

        Book book = Book.builder()
                .id(1L)
                .title("Test Book")
                .build();

        FileProcessResult processResult = FileProcessResult.builder()
                .book(book)
                .build();
        when(processor.processFile(any())).thenReturn(processResult);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.createTempFile(anyString(), anyString())).thenReturn(tempDir.resolve("temp-file"));
            filesMock.when(() -> Files.copy(any(Path.class), any(Path.class), any())).thenReturn(1024L);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(tempDir);
            filesMock.when(() -> Files.move(any(Path.class), any(Path.class), any())).thenReturn(tempDir);
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);

            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(1, result.getSuccessfullyImported());
            assertEquals(0, result.getFailed());
        }
    }

    @Test
    void finalizeImport_WhenLibraryNotFound_ShouldFail() throws Exception {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setDefaultLibraryId(999L);
        request.setDefaultPathId(1L);

        when(bookdropFileRepository.findAllExcludingIdsFlat(any())).thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(999L)).thenReturn(Optional.empty());

        BookMetadata metadata = new BookMetadata();
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadata);

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);

            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(0, result.getSuccessfullyImported());
            assertEquals(1, result.getFailed());
        }
    }

    @Test
    void discardSelectedFiles_WhenSelectAllTrue_ShouldDeleteAllExceptExcluded() throws IOException {
        List<Long> excludedIds = List.of(2L);
        BookdropFileEntity fileToDelete = new BookdropFileEntity();
        fileToDelete.setId(1L);
        fileToDelete.setFilePath(tempDir.resolve("file-to-delete.pdf").toString());

        Files.createFile(tempDir.resolve("file-to-delete.pdf"));

        when(bookdropFileRepository.findAll()).thenReturn(List.of(fileToDelete));
        when(appProperties.getBookdropFolder()).thenReturn(tempDir.toString());
        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(java.util.stream.Stream.of(tempDir));
            filesMock.when(() -> Files.isDirectory(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.isRegularFile(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.list(any(Path.class))).thenReturn(java.util.stream.Stream.empty());

            bookDropService.discardSelectedFiles(true, excludedIds, null);

            verify(bookdropFileRepository).findAll();
            verify(bookdropFileRepository).deleteAllById(List.of(1L));
            verify(bookdropNotificationService).sendBookdropFileSummaryNotification();
            verify(bookdropMonitoringService).pauseMonitoring();
            verify(bookdropMonitoringService).resumeMonitoring();
        }
    }

    @Test
    void discardSelectedFiles_WhenSelectAllFalse_ShouldDeleteOnlySelected() throws IOException {
        List<Long> selectedIds = List.of(1L);
        when(bookdropFileRepository.findAllById(selectedIds)).thenReturn(List.of(bookdropFileEntity));
        when(appProperties.getBookdropFolder()).thenReturn(tempDir.toString());
        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.walk(any(Path.class))).thenReturn(java.util.stream.Stream.of(tempDir));
            filesMock.when(() -> Files.isDirectory(any(Path.class))).thenReturn(false);
            filesMock.when(() -> Files.isRegularFile(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.deleteIfExists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.list(any(Path.class))).thenReturn(java.util.stream.Stream.empty());

            bookDropService.discardSelectedFiles(false, null, selectedIds);

            verify(bookdropFileRepository).findAllById(selectedIds);
            verify(bookdropFileRepository).deleteAllById(List.of(1L));
            verify(bookdropNotificationService).sendBookdropFileSummaryNotification();
        }
    }

    @Test
    void discardSelectedFiles_WhenBookdropFolderDoesNotExist_ShouldHandleGracefully() {
        when(appProperties.getBookdropFolder()).thenReturn("/non-existent-path");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);

            bookDropService.discardSelectedFiles(true, null, null);

            verify(bookdropMonitoringService).pauseMonitoring();
            verify(bookdropMonitoringService).resumeMonitoring();
        }
    }

    @Test
    void finalizeImport_WhenSourceFileDoesNotExist_ShouldDeleteFromDB() throws Exception {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);

        BookdropFileEntity missingFileEntity = new BookdropFileEntity();
        missingFileEntity.setId(2L);
        missingFileEntity.setFileName("missing-file.pdf");
        missingFileEntity.setFilePath("/non-existent/missing-file.pdf");
        missingFileEntity.setOriginalMetadata("{\"title\":\"Missing Book\"}");
        missingFileEntity.setFetchedMetadata(null);

        when(bookdropFileRepository.findAllExcludingIdsFlat(any())).thenReturn(List.of(2L));
        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(missingFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));

        BookMetadata metadata = new BookMetadata();
        metadata.setTitle("Missing Book");
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadata);
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(tempDir.resolve("target-file.pdf"));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(Path.of("/non-existent/missing-file.pdf"))).thenReturn(false);

            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            verify(bookdropFileRepository).deleteById(2L);
            verify(bookdropNotificationService).sendBookdropFileSummaryNotification();
            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(0, result.getSuccessfullyImported());
            assertEquals(1, result.getFailed());
        }
    }

    @Test
    void finalizeImport_WhenIOExceptionDuringMove_ShouldHandleGracefully() throws Exception {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);

        when(bookdropFileRepository.findAllExcludingIdsFlat(any())).thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));

        BookMetadata metadata = new BookMetadata();
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(metadata);
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(tempDir.resolve("target-file.pdf"));

        try (MockedStatic<Files> filesMock = mockStatic(Files.class, withSettings().lenient())) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.createTempFile(anyString(), anyString()))
                    .thenThrow(new IOException("Disk full"));

            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(0, result.getSuccessfullyImported());
            assertEquals(1, result.getFailed());
        }
    }

    @Test
    void finalizeImport_WhenProcessingSucceeds_ShouldDeleteSourceFileFromBookdrop() throws Exception {
        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(true);
        request.setDefaultLibraryId(1L);
        request.setDefaultPathId(1L);
        request.setExcludedIds(List.of());

        when(bookdropFileRepository.findAllExcludingIdsFlat(any())).thenReturn(List.of(1L));
        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));
        
        LibraryPathEntity pathEntity = new LibraryPathEntity();
        pathEntity.setId(1L);
        pathEntity.setPath("/books");
        when(libraryPathRepository.findById(1L)).thenReturn(Optional.of(pathEntity));

        lenient().when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(new BookMetadata());
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(tempDir.resolve("moved-book.pdf"));

        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);

        Book book = Book.builder().id(1L).title("Test Book").build();
        FileProcessResult processResult = FileProcessResult.builder().book(book).build();
        when(processor.processFile(any())).thenReturn(processResult);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        
        when(bookRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        doNothing().when(bookdropFileRepository).deleteById(any());
        doNothing().when(bookdropNotificationService).sendBookdropFileSummaryNotification();
        doNothing().when(notificationService).sendMessage(any(), any());
        lenient().doNothing().when(metadataRefreshService).updateBookMetadata(any());

        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());

        Path sourcePath = Path.of(bookdropFileEntity.getFilePath());
        Path tempPath = tempDir.resolve("temp-file");
        Path targetPath = tempDir.resolve("moved-book.pdf");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.createTempFile(anyString(), anyString())).thenReturn(tempPath);
            filesMock.when(() -> Files.copy(any(Path.class), any(Path.class), any())).thenReturn(tempPath);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(tempDir);
            filesMock.when(() -> Files.move(any(Path.class), any(Path.class), any())).thenReturn(targetPath);
            
            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(1, result.getSuccessfullyImported());
            assertEquals(0, result.getFailed());

            filesMock.verify(() -> Files.delete(sourcePath), times(1));
        }
    }

    @Test
    void finalizeImport_WhenProcessingFails_ShouldPreserveSourceFileInBookdrop() throws Exception {
        BookdropFinalizeRequest.BookdropFinalizeFile finalizeFile = new BookdropFinalizeRequest.BookdropFinalizeFile();
        finalizeFile.setFileId(1L);
        finalizeFile.setLibraryId(1L);
        finalizeFile.setPathId(1L);
        finalizeFile.setMetadata(new BookMetadata());

        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(false);
        request.setFiles(List.of(finalizeFile));

        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(new BookMetadata());
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(tempDir.resolve("moved-book.pdf"));

        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);

        when(processor.processFile(any())).thenThrow(new RuntimeException("Processing failed"));

        Path sourcePath = Path.of(bookdropFileEntity.getFilePath());
        Path tempPath = tempDir.resolve("temp-file");
        Path targetPath = tempDir.resolve("moved-book.pdf");

        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
            filesMock.when(() -> Files.createTempFile(anyString(), anyString())).thenReturn(tempPath);
            filesMock.when(() -> Files.copy(any(Path.class), any(Path.class), any())).thenReturn(tempPath);
            filesMock.when(() -> Files.createDirectories(any(Path.class))).thenReturn(tempDir);
            filesMock.when(() -> Files.move(any(Path.class), any(Path.class), any())).thenReturn(targetPath);
            
            filesMock.when(() -> Files.deleteIfExists(targetPath)).thenReturn(true);

            BookdropFinalizeResult result = bookDropService.finalizeImport(request);

            assertNotNull(result);
            assertEquals(1, result.getTotalFiles());
            assertEquals(0, result.getSuccessfullyImported());
            assertEquals(1, result.getFailed());

            filesMock.verify(() -> Files.delete(sourcePath), never());
            filesMock.verify(() -> Files.deleteIfExists(targetPath), times(1));
        }
    }

    @Test
    void finalizeImport_WhenMetadataRefreshFails_ShouldPreserveSourceFileInBookdrop() throws Exception {
        Path sourceFile = tempDir.resolve("metadata-failure-test-book.pdf");
        Files.createFile(sourceFile);
        assertTrue(Files.exists(sourceFile), "Source file should exist");
        bookdropFileEntity.setFilePath(sourceFile.toString());

        Path targetDir = tempDir.resolve("library");
        Files.createDirectories(targetDir);

        BookdropFinalizeRequest.BookdropFinalizeFile finalizeFile = new BookdropFinalizeRequest.BookdropFinalizeFile();
        finalizeFile.setFileId(1L);
        finalizeFile.setLibraryId(1L);
        finalizeFile.setPathId(1L);
        finalizeFile.setMetadata(new BookMetadata());

        BookdropFinalizeRequest request = new BookdropFinalizeRequest();
        request.setSelectAll(false);
        request.setFiles(List.of(finalizeFile));

        when(bookdropFileRepository.findAllById(any())).thenReturn(List.of(bookdropFileEntity));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(libraryEntity));
        when(objectMapper.readValue(anyString(), eq(BookMetadata.class))).thenReturn(new BookMetadata());
        when(fileMovingHelper.getFileNamingPattern(libraryEntity)).thenReturn("{title}");
        when(fileMovingHelper.generateNewFilePath(anyString(), any(), anyString(), anyString()))
                .thenReturn(targetDir.resolve("moved-book.pdf"));

        BookFileProcessor processor = mock(BookFileProcessor.class);
        when(processorRegistry.getProcessorOrThrow(any())).thenReturn(processor);

        Book book = Book.builder().id(1L).title("Test Book").build();
        FileProcessResult processResult = FileProcessResult.builder().book(book).build();
        when(processor.processFile(any())).thenReturn(processResult);

        BookEntity bookEntity = new BookEntity();
        bookEntity.setId(1L);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));

        doNothing().when(bookdropFileRepository).deleteById(any());
        doNothing().when(bookdropNotificationService).sendBookdropFileSummaryNotification();
        doNothing().when(notificationService).sendMessage(any(), any());

        doThrow(new RuntimeException("Metadata refresh failed")).when(metadataRefreshService).updateBookMetadata(any());

        when(appProperties.getPathConfig()).thenReturn(tempDir.toString());

        BookdropFinalizeResult result = bookDropService.finalizeImport(request);

        assertNotNull(result);
        assertEquals(1, result.getTotalFiles());
        assertEquals(0, result.getSuccessfullyImported());
        assertEquals(1, result.getFailed());

        assertTrue(Files.exists(sourceFile), "Source file should be preserved on failure");
        assertFalse(Files.exists(targetDir.resolve("moved-book.pdf")), "Target file should be cleaned up on failure");
    }
}
