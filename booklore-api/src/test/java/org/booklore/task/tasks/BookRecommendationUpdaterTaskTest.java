package org.booklore.task.tasks;

import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.TaskType;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.recommender.BookVectorService;
import org.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

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

    @Captor
    private ArgumentCaptor<Map<Long, Set<BookRecommendationLite>>> recommendationsCaptor;

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
        when(bookQueryService.countAllNonDeleted()).thenReturn(0L);
        when(bookQueryService.getAllFullBookEntitiesBatch(any())).thenReturn(Collections.emptyList());

        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskType.UPDATE_BOOK_RECOMMENDATIONS, response.getTaskType());
        verify(bookQueryService).countAllNonDeleted();
        verify(bookQueryService).getAllFullBookEntitiesBatch(any());
        verify(bookQueryService).saveRecommendationsInBatches(eq(Collections.emptyMap()), anyInt());
        verify(notificationService, atLeastOnce()).sendMessage(eq(Topic.TASK_PROGRESS), any());
    }

    @Test
    void execute_shouldProcessBooks() {
        BookEntity book1 = BookEntity.builder().id(1L).metadata(BookMetadataEntity.builder().title("B1").build()).build();
        BookEntity book2 = BookEntity.builder().id(2L).metadata(BookMetadataEntity.builder().title("B2").build()).build();
        List<BookEntity> books = List.of(book1, book2);

        when(bookQueryService.countAllNonDeleted()).thenReturn(2L);
        when(bookQueryService.getAllFullBookEntitiesBatch(any())).thenReturn(books).thenReturn(Collections.emptyList());
        when(vectorService.generateEmbedding(any())).thenReturn(new double[]{0.1, 0.2});
        when(vectorService.serializeVector(any())).thenReturn("[0.1, 0.2]");
        when(vectorService.cosineSimilarity(any(), any())).thenReturn(0.9);
        when(vectorService.findTopKSimilar(any(), anyList(), anyInt())).thenAnswer(invocation -> {
            List<BookVectorService.ScoredBook> candidates = invocation.getArgument(1);
            return candidates.isEmpty() ? new ArrayList<>() : List.of(candidates.getFirst());
        });

        TaskCreateResponse response = task.execute(request);

        assertEquals("task-123", response.getTaskId());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());

        verify(bookQueryService).compareAndSaveEmbeddings(any());
        verify(vectorService, times(2)).generateEmbedding(any());
        verify(bookQueryService).saveRecommendationsInBatches(recommendationsCaptor.capture(), anyInt());

        Map<Long, Set<BookRecommendationLite>> recommendations = recommendationsCaptor.getValue();
        assertNotNull(recommendations);
        assertEquals(2, recommendations.size(), "Should have recommendations for both books");
        assertTrue(recommendations.containsKey(1L), "Should have recommendations for book 1");
        assertTrue(recommendations.containsKey(2L), "Should have recommendations for book 2");
        // Each book should recommend the other
        recommendations.values().forEach(recs ->
                assertFalse(recs.isEmpty(), "Each book should have at least one recommendation"));
    }

    @Test
    void execute_shouldFail_whenEmbeddingGenerationThrows() {
        BookEntity book1 = BookEntity.builder().id(1L).metadata(BookMetadataEntity.builder().title("B1").build()).build();

        when(bookQueryService.countAllNonDeleted()).thenReturn(1L);
        when(bookQueryService.getAllFullBookEntitiesBatch(any())).thenReturn(List.of(book1));
        when(vectorService.generateEmbedding(any())).thenThrow(new RuntimeException("Embedding failed"));

        assertThrows(RuntimeException.class, () -> task.execute(request));
    }

    @Test
    void execute_shouldContinue_whenSimilarityCalculationThrows() {
        BookEntity book1 = BookEntity.builder().id(1L).metadata(BookMetadataEntity.builder().title("B1").build()).build();
        BookEntity book2 = BookEntity.builder().id(2L).metadata(BookMetadataEntity.builder().title("B2").build()).build();
        List<BookEntity> books = List.of(book1, book2);

        when(bookQueryService.countAllNonDeleted()).thenReturn(2L);
        when(bookQueryService.getAllFullBookEntitiesBatch(any())).thenReturn(books).thenReturn(Collections.emptyList());
        when(vectorService.generateEmbedding(any())).thenReturn(new double[]{0.1});
        when(vectorService.serializeVector(any())).thenReturn("[0.1]");
        when(vectorService.cosineSimilarity(any(), any())).thenThrow(new RuntimeException("Math error"));

        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(bookQueryService).saveRecommendationsInBatches(any(Map.class), anyInt());
    }
}
