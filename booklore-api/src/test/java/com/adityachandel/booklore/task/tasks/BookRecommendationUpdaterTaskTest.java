package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.recommender.BookVectorService;
import com.adityachandel.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookRecommendationUpdaterTaskTest {

    @Mock
    private BookQueryService bookQueryService;
    @Mock
    private BookVectorService vectorService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BookRecommendationUpdaterTask task;

    private BookLoreUser user;
    private TaskCreateRequest request;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder()
                .permissions(new BookLoreUser.UserPermissions())
                .build();
        request = new TaskCreateRequest();
        request.setTaskId("task-123");
    }

    @Test
    void validatePermissions_shouldThrowException_whenUserCannotAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(false);
        assertThrows(APIException.class, () -> task.validatePermissions(user, request));
    }

    @Test
    void execute_shouldHandleEmptyBookList() {
        when(bookQueryService.getAllFullBookEntities()).thenReturn(Collections.emptyList());

        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskType.UPDATE_BOOK_RECOMMENDATIONS, response.getTaskType());
        verify(bookQueryService).getAllFullBookEntities();
        verify(bookQueryService).saveAll(anyList()); // Should be called with empty list
        verify(notificationService, atLeastOnce()).sendMessage(eq(Topic.TASK_PROGRESS), any());
    }

    @Test
    void execute_shouldProcessBooks() {
        BookEntity book1 = BookEntity.builder().id(1L).metadata(BookMetadataEntity.builder().title("B1").build()).build();
        BookEntity book2 = BookEntity.builder().id(2L).metadata(BookMetadataEntity.builder().title("B2").build()).build();
        List<BookEntity> books = List.of(book1, book2);

        when(bookQueryService.getAllFullBookEntities()).thenReturn(books);
        when(vectorService.generateEmbedding(any())).thenReturn(new double[]{0.1, 0.2});
        when(vectorService.serializeVector(any())).thenReturn("[0.1, 0.2]");
        // Lenient stubs for similarity calculations
        lenient().when(vectorService.cosineSimilarity(any(), any())).thenReturn(0.9);
        lenient().when(vectorService.findTopKSimilar(any(), anyList(), anyInt())).thenReturn(new ArrayList<>());

        TaskCreateResponse response = task.execute(request);

        assertEquals("task-123", response.getTaskId());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());

        org.mockito.ArgumentCaptor<List<BookEntity>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(bookQueryService).saveAll(captor.capture());
        
        List<BookEntity> savedBooks = captor.getValue();
        assertEquals(2, savedBooks.size());
        // Verify embeddings were set
        assertNotNull(savedBooks.get(0).getMetadata().getEmbeddingVector());
        verify(vectorService, times(2)).generateEmbedding(any());
    }

    @Test
    void execute_shouldFail_whenEmbeddingGenerationThrows() {
        BookEntity book1 = BookEntity.builder().id(1L).build();
        when(bookQueryService.getAllFullBookEntities()).thenReturn(List.of(book1));
        when(vectorService.generateEmbedding(any())).thenThrow(new RuntimeException("Embedding failed"));

        assertThrows(RuntimeException.class, () -> task.execute(request));
    }

    @Test
    void execute_shouldContinue_whenSimilarityCalculationThrows() {
        BookEntity book1 = BookEntity.builder().id(1L).metadata(BookMetadataEntity.builder().title("B1").build()).build();
        BookEntity book2 = BookEntity.builder().id(2L).metadata(BookMetadataEntity.builder().title("B2").build()).build();
        List<BookEntity> books = List.of(book1, book2);

        when(bookQueryService.getAllFullBookEntities()).thenReturn(books);
        when(vectorService.generateEmbedding(any())).thenReturn(new double[]{0.1});
        when(vectorService.serializeVector(any())).thenReturn("[0.1]");
        
        // Mock similarity to throw for first book, pass for second (actually it iterates as target)
        // target=book1, candidate=book2. 
        when(vectorService.cosineSimilarity(any(), any())).thenThrow(new RuntimeException("Math error"));

        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        // Should still try to save updates (even if empty list added)
        verify(bookQueryService).saveAll(anyList());
    }
}
