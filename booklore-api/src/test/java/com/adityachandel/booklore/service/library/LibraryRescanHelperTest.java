package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.MetadataUpdateContext;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MetadataReplaceMode;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.model.websocket.TaskProgressPayload;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.metadata.BookMetadataUpdater;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import com.adityachandel.booklore.task.options.RescanLibraryContext;
import com.adityachandel.booklore.task.TaskCancellationManager;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.task.options.LibraryRescanOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryRescanHelperTest {

    @Mock private LibraryRepository libraryRepository;
    @Mock private MetadataExtractorFactory metadataExtractorFactory;
    @Mock private BookMetadataUpdater bookMetadataUpdater;
    @Mock private NotificationService notificationService;
    @Mock private TaskCancellationManager cancellationManager;
    @Mock private BookRepository bookRepository;
    @InjectMocks private LibraryRescanHelper libraryRescanHelper;

    @Captor private ArgumentCaptor<TaskProgressPayload> payloadCaptor;
    @Captor private ArgumentCaptor<MetadataUpdateContext> metadataContextCaptor;

    private LibraryEntity library;
    private RescanLibraryContext rescanContext;
    private String taskId;

    @BeforeEach
    void setUp() {
        library = new LibraryEntity();
        library.setId(1L);
        library.setName("Test Library");
        library.setBookEntities(new ArrayList<>());

        LibraryRescanOptions options = LibraryRescanOptions.builder()
                .metadataReplaceMode(MetadataReplaceMode.REPLACE_ALL)
                .updateMetadataFromFiles(true).build();

        rescanContext = RescanLibraryContext.builder()
                .libraryId(1L)
                .options(options)
                .build();

        taskId = "task-123";
    }

    @Test
    void handleRescanOptions_shouldThrowException_whenLibraryNotFound() {
        when(libraryRepository.findById(1L)).thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                libraryRescanHelper.handleRescanOptions(rescanContext, taskId)
        );
        assertTrue(exception.getMessage().contains("LIBRARY_NOT_FOUND") ||
                   exception.getMessage().contains("1"));
        verify(libraryRepository).findById(1L);
        verifyNoInteractions(metadataExtractorFactory, bookMetadataUpdater);
    }

    @Test
    void handleRescanOptions_shouldProcessAllBooks_whenLibraryHasBooks() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        BookEntity book2 = createBookEntity(2L, "book2.pdf", BookFileType.PDF);

        BookMetadata metadata1 = new BookMetadata();
        metadata1.setTitle("Book 1");
        BookMetadata metadata2 = new BookMetadata();
        metadata2.setTitle("Book 2");

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1, book2));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileType.EPUB), any(File.class))).thenReturn(metadata1);
        when(metadataExtractorFactory.extractMetadata(eq(BookFileType.PDF), any(File.class))).thenReturn(metadata2);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(libraryRepository).findById(1L);
        verify(metadataExtractorFactory, times(2)).extractMetadata(any(BookFileType.class), any(File.class));
        verify(bookMetadataUpdater, times(2)).setBookMetadata(any(MetadataUpdateContext.class));
        verify(notificationService, times(4)).sendMessage(eq(Topic.TASK_PROGRESS), any(TaskProgressPayload.class));
    }

    @Test
    void handleRescanOptions_shouldSkipDeletedBooks() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        BookEntity book2 = createBookEntity(2L, "book2.pdf", BookFileType.PDF);
        book2.setDeleted(true);

        BookMetadata metadata = new BookMetadata();
        metadata.setTitle("Book 1");

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1, book2));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(metadataExtractorFactory, times(1)).extractMetadata(any(BookFileType.class), any(File.class));
        verify(bookMetadataUpdater, times(1)).setBookMetadata(any(MetadataUpdateContext.class));
    }

    @Test
    void handleRescanOptions_shouldSkipNullBooks() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        java.util.List<BookEntity> books = new ArrayList<>();
        books.add(book1);
        books.add(null);

        BookMetadata metadata = new BookMetadata();
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(books);
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(metadataExtractorFactory, times(1)).extractMetadata(any(BookFileType.class), any(File.class));
        verify(bookMetadataUpdater, times(1)).setBookMetadata(any(MetadataUpdateContext.class));
    }

    @Test
    void handleRescanOptions_shouldContinue_whenMetadataExtractionReturnsNull() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        BookEntity book2 = createBookEntity(2L, "book2.pdf", BookFileType.PDF);

        BookMetadata metadata2 = new BookMetadata();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1, book2));
        when(metadataExtractorFactory.extractMetadata(eq(BookFileType.EPUB), any(File.class))).thenReturn(null);
        when(metadataExtractorFactory.extractMetadata(eq(BookFileType.PDF), any(File.class))).thenReturn(metadata2);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(metadataExtractorFactory, times(2)).extractMetadata(any(BookFileType.class), any(File.class));
        verify(bookMetadataUpdater, times(1)).setBookMetadata(any(MetadataUpdateContext.class));
    }

    @Test
    void handleRescanOptions_shouldContinue_whenMetadataUpdateThrowsException() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        BookEntity book2 = createBookEntity(2L, "book2.pdf", BookFileType.PDF);

        BookMetadata metadata1 = new BookMetadata();
        BookMetadata metadata2 = new BookMetadata();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1, book2));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class)))
                .thenReturn(metadata1, metadata2);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);
        doThrow(new RuntimeException("Update failed")).doNothing()
                .when(bookMetadataUpdater).setBookMetadata(any(MetadataUpdateContext.class));

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(metadataExtractorFactory, times(2)).extractMetadata(any(BookFileType.class), any(File.class));
        verify(bookMetadataUpdater, times(2)).setBookMetadata(any(MetadataUpdateContext.class));
    }

    @Test
    void handleRescanOptions_shouldCancel_whenTaskCancellationRequested() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);
        BookEntity book2 = createBookEntity(2L, "book2.pdf", BookFileType.PDF);

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1, book2));
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false, true);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(cancellationManager, atLeast(1)).isTaskCancelled(taskId);
        verify(notificationService, atLeastOnce()).sendMessage(eq(Topic.TASK_PROGRESS), payloadCaptor.capture());

        List<TaskProgressPayload> payloads = payloadCaptor.getAllValues();
        assertTrue(payloads.stream().anyMatch(p -> p.getTaskStatus() == TaskStatus.CANCELLED));
    }

    @Test
    void handleRescanOptions_shouldSendProgressNotifications() {
        BookEntity book1 = createBookEntity(1L, "book1.epub", BookFileType.EPUB);

        BookMetadata metadata = new BookMetadata();
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book1));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(notificationService, times(3)).sendMessage(eq(Topic.TASK_PROGRESS), payloadCaptor.capture());

        List<TaskProgressPayload> payloads = payloadCaptor.getAllValues();
        assertEquals(3, payloads.size());
        assertEquals(0, payloads.getFirst().getProgress());
        assertEquals(TaskStatus.IN_PROGRESS, payloads.get(0).getTaskStatus());
        assertEquals(TaskType.REFRESH_LIBRARY_METADATA, payloads.get(0).getTaskType());
        assertEquals(TaskStatus.IN_PROGRESS, payloads.get(1).getTaskStatus());
        assertEquals(100, payloads.get(2).getProgress());
        assertEquals(TaskStatus.COMPLETED, payloads.get(2).getTaskStatus());
    }

    @Test
    void handleRescanOptions_shouldHandleEmptyLibrary() {
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(new ArrayList<>());
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(libraryRepository).findById(1L);
        verify(notificationService, times(2)).sendMessage(eq(Topic.TASK_PROGRESS), payloadCaptor.capture());

        List<TaskProgressPayload> payloads = payloadCaptor.getAllValues();
        assertEquals(TaskStatus.COMPLETED, payloads.get(1).getTaskStatus());
        assertEquals(100, payloads.get(1).getProgress());
    }

    @Test
    void handleRescanOptions_shouldSetCorrectMetadataUpdateContext() {
        BookEntity book = createBookEntity(1L, "book1.epub", BookFileType.EPUB);

        BookMetadata metadata = new BookMetadata();
        metadata.setTitle("Test Book");

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);

        libraryRescanHelper.handleRescanOptions(rescanContext, taskId);

        verify(bookMetadataUpdater).setBookMetadata(metadataContextCaptor.capture());
        MetadataUpdateContext capturedContext = metadataContextCaptor.getValue();

        assertEquals(book, capturedContext.getBookEntity());
        assertEquals(metadata, capturedContext.getMetadataUpdateWrapper().getMetadata());
        assertEquals(MetadataReplaceMode.REPLACE_ALL, capturedContext.getReplaceMode());
        assertFalse(capturedContext.isUpdateThumbnail());
        assertFalse(capturedContext.isMergeCategories());
    }

    @Test
    void handleRescanOptions_shouldHandleNullTaskId() {
        BookEntity book = createBookEntity(1L, "book1.epub", BookFileType.EPUB);

        BookMetadata metadata = new BookMetadata();
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);

        libraryRescanHelper.handleRescanOptions(rescanContext, null);

        verify(bookMetadataUpdater).setBookMetadata(any(MetadataUpdateContext.class));
        verify(notificationService, times(3)).sendMessage(eq(Topic.TASK_PROGRESS), payloadCaptor.capture());

        List<TaskProgressPayload> payloads = payloadCaptor.getAllValues();
        assertTrue(payloads.stream().allMatch(p -> p.getTaskId() == null));
    }

    @Test
    void handleRescanOptions_shouldContinue_whenNotificationFails() {
        BookEntity book = createBookEntity(1L, "book1.epub", BookFileType.EPUB);

        BookMetadata metadata = new BookMetadata();
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookRepository.findAllWithMetadataByLibraryId(1L)).thenReturn(List.of(book));
        when(metadataExtractorFactory.extractMetadata(any(BookFileType.class), any(File.class))).thenReturn(metadata);
        when(cancellationManager.isTaskCancelled(taskId)).thenReturn(false);
        doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).sendMessage(any(Topic.class), any(TaskProgressPayload.class));

        assertDoesNotThrow(() -> libraryRescanHelper.handleRescanOptions(rescanContext, taskId));
        verify(bookMetadataUpdater).setBookMetadata(any(MetadataUpdateContext.class));
    }

    private BookEntity createBookEntity(Long id, String fileName, BookFileType bookType) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath("/test/path");
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setFileName(fileName);
        book.setBookType(bookType);
        book.setDeleted(false);
        book.setLibraryPath(libraryPath);
        book.setFileSubPath("");
        return book;
    }
}
