package org.grimmory.task.tasks;

import org.grimmory.exception.APIException;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.request.TaskCreateRequest;
import org.grimmory.model.dto.response.TaskCreateResponse;
import org.grimmory.model.enums.TaskType;
import org.grimmory.repository.MetadataFetchJobRepository;
import org.grimmory.task.TaskStatus;
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
class TempFetchedMetadataCleanupTaskTest {

    @Mock
    private MetadataFetchJobRepository metadataFetchJobRepository;

    @InjectMocks
    private TempFetchedMetadataCleanupTask tempFetchedMetadataCleanupTask;

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
        assertThrows(APIException.class, () -> tempFetchedMetadataCleanupTask.validatePermissions(user, request));
    }

    @Test
    void validatePermissions_shouldPass_whenUserCanAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(true);
        assertDoesNotThrow(() -> tempFetchedMetadataCleanupTask.validatePermissions(user, request));
    }

    @Test
    void execute_shouldDeleteOldRecords_whenTriggeredByCron() {
        request.setTriggeredByCron(true);
        when(metadataFetchJobRepository.deleteAllByCompletedAtBefore(any(Instant.class))).thenReturn(5);

        TaskCreateResponse response = tempFetchedMetadataCleanupTask.execute(request);

        assertEquals(TaskType.CLEANUP_TEMP_METADATA, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(metadataFetchJobRepository).deleteAllByCompletedAtBefore(any(Instant.class));
        verify(metadataFetchJobRepository, never()).deleteAllRecords();
    }

    @Test
    void execute_shouldDeleteAllRecords_whenNotTriggeredByCron() {
        request.setTriggeredByCron(false);
        when(metadataFetchJobRepository.deleteAllRecords()).thenReturn(10);

        TaskCreateResponse response = tempFetchedMetadataCleanupTask.execute(request);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(metadataFetchJobRepository).deleteAllRecords();
        verify(metadataFetchJobRepository, never()).deleteAllByCompletedAtBefore(any());
    }

    @Test
    void execute_shouldReturnFailed_whenRepositoryThrowsException() {
        request.setTriggeredByCron(false);
        when(metadataFetchJobRepository.deleteAllRecords()).thenThrow(new RuntimeException("DB Error"));

        TaskCreateResponse response = tempFetchedMetadataCleanupTask.execute(request);

        assertEquals(TaskStatus.FAILED, response.getStatus());
    }
}
