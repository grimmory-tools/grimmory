package org.booklore.task.tasks;

import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.repository.BookRepository;
import org.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletedBooksCleanupTaskTest {

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private DeletedBooksCleanupTask deletedBooksCleanupTask;

    private BookLoreUser user;
    private TaskCreateRequest request;

    @BeforeEach
    void setUp() {
        user = BookLoreUser.builder()
                .permissions(new BookLoreUser.UserPermissions())
                .build();
        request = new TaskCreateRequest();
    }

    @Test
    void validatePermissions_shouldThrowException_whenUserCannotAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(false);
        assertThrows(APIException.class, () -> deletedBooksCleanupTask.validatePermissions(user, request));
    }

    @Test
    void validatePermissions_shouldPass_whenUserCanAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(true);
        assertDoesNotThrow(() -> deletedBooksCleanupTask.validatePermissions(user, request));
    }

    @Test
    void execute_shouldDeleteOldRecords_whenTriggeredByCron() {
        request.setTriggeredByCron(true);
        when(bookRepository.deleteSoftDeletedBefore(any(Instant.class))).thenReturn(5);

        TaskCreateResponse response = deletedBooksCleanupTask.execute(request);

        assertEquals(TaskType.CLEANUP_DELETED_BOOKS, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(bookRepository).deleteSoftDeletedBefore(any(Instant.class));
        verify(bookRepository, never()).deleteAllSoftDeleted();
    }

    @Test
    void execute_shouldDeleteAllRecords_whenNotTriggeredByCron() {
        request.setTriggeredByCron(false);
        when(bookRepository.deleteAllSoftDeleted()).thenReturn(10);

        TaskCreateResponse response = deletedBooksCleanupTask.execute(request);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(bookRepository).deleteAllSoftDeleted();
        verify(bookRepository, never()).deleteSoftDeletedBefore(any());
    }

    @Test
    void execute_shouldReturnFailed_whenRepositoryThrowsException() {
        request.setTriggeredByCron(false);
        when(bookRepository.deleteAllSoftDeleted()).thenThrow(new RuntimeException("DB Error"));

        TaskCreateResponse response = deletedBooksCleanupTask.execute(request);

        assertEquals(TaskStatus.FAILED, response.getStatus());
    }

    @Test
    void getMetadata_shouldReturnCount() {
        when(bookRepository.countAllSoftDeleted()).thenReturn(42L);
        String metadata = deletedBooksCleanupTask.getMetadata();
        assertTrue(metadata.contains("42"));
    }
}
