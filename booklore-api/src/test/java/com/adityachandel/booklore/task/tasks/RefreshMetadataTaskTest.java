package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.MetadataRefreshRequest;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.service.metadata.MetadataRefreshService;
import com.adityachandel.booklore.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshMetadataTaskTest {

    @Mock
    private MetadataRefreshService metadataRefreshService;

    @InjectMocks
    private RefreshMetadataTask refreshMetadataTask;

    private BookLoreUser user;
    private TaskCreateRequest taskCreateRequest;
    private MetadataRefreshRequest metadataRefreshRequest;

    @BeforeEach
    void setUp() {
        BookLoreUser.UserPermissions permissions = new BookLoreUser.UserPermissions();
        user = BookLoreUser.builder().permissions(permissions).build();
        
        taskCreateRequest = mock(TaskCreateRequest.class);
        metadataRefreshRequest = MetadataRefreshRequest.builder().build();
        
        when(taskCreateRequest.getOptions(MetadataRefreshRequest.class)).thenReturn(metadataRefreshRequest);
    }

    @Test
    void validatePermissions_shouldThrowException_whenLibraryRefreshAndNoBulkPermission() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.LIBRARY);

        assertThrows(APIException.class, () -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }
    
    @Test
    void validatePermissions_shouldPass_whenLibraryRefreshAndHasBulkPermission() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.LIBRARY);
        user.getPermissions().setCanBulkAutoFetchMetadata(true);

        assertDoesNotThrow(() -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldThrowException_whenMultipleBooksAndNoBulkPermission() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.BOOKS);
        metadataRefreshRequest.setBookIds(Set.of(1L, 2L));

        assertThrows(APIException.class, () -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldPass_whenMultipleBooksAndHasBulkPermission() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.BOOKS);
        metadataRefreshRequest.setBookIds(Set.of(1L, 2L));
        user.getPermissions().setCanBulkAutoFetchMetadata(true);

        assertDoesNotThrow(() -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldPass_whenSingleBookAndNoBulkPermission() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.BOOKS);
        metadataRefreshRequest.setBookIds(Set.of(1L));

        assertDoesNotThrow(() -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldThrowException_whenUserPermissionsAreNull() {
        user.setPermissions(null);
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.LIBRARY);

        assertThrows(APIException.class, () -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldPass_whenNoBookIdsAndNoBulkPermission_AssumingSingleOrNoAction() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.BOOKS);
        metadataRefreshRequest.setBookIds(null);

        assertDoesNotThrow(() -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void validatePermissions_shouldPass_whenEmptyBookIdSet() {
        metadataRefreshRequest.setRefreshType(MetadataRefreshRequest.RefreshType.BOOKS);
        metadataRefreshRequest.setBookIds(Collections.emptySet());

        assertDoesNotThrow(() -> refreshMetadataTask.validatePermissions(user, taskCreateRequest));
    }

    @Test
    void execute_shouldCallServiceAndReturnCompleted() {
        when(taskCreateRequest.getTaskId()).thenReturn("task-1");
        TaskCreateResponse response = refreshMetadataTask.execute(taskCreateRequest);

        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        assertEquals("task-1", response.getTaskId());
        
        ArgumentCaptor<MetadataRefreshRequest> requestCaptor = ArgumentCaptor.forClass(MetadataRefreshRequest.class);
        ArgumentCaptor<String> taskIdCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(metadataRefreshService).refreshMetadata(requestCaptor.capture(), taskIdCaptor.capture());
        assertEquals(metadataRefreshRequest, requestCaptor.getValue());
        assertEquals("task-1", taskIdCaptor.getValue());
    }

    @Test
    void execute_shouldPropagateException_whenServiceThrows() {
        when(taskCreateRequest.getTaskId()).thenReturn("task-1");
        doThrow(new RuntimeException("Service error")).when(metadataRefreshService).refreshMetadata(metadataRefreshRequest, "task-1");

        assertThrows(RuntimeException.class, () -> refreshMetadataTask.execute(taskCreateRequest));
    }

    @Test
    void execute_shouldHandleCancellation() {
        when(taskCreateRequest.getTaskId()).thenReturn("task-1");
        doThrow(new CancellationException("Task cancelled")).when(metadataRefreshService).refreshMetadata(metadataRefreshRequest, "task-1");

        assertThrows(CancellationException.class, () -> refreshMetadataTask.execute(taskCreateRequest));
    }
}
