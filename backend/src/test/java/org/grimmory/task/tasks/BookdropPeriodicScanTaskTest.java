package org.grimmory.task.tasks;

import org.grimmory.exception.APIException;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.request.TaskCreateRequest;
import org.grimmory.model.dto.response.TaskCreateResponse;
import org.grimmory.model.enums.TaskType;
import org.grimmory.service.bookdrop.BookdropMonitoringService;
import org.grimmory.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookdropPeriodicScanTaskTest {

    @Mock
    private BookdropMonitoringService bookdropMonitoringService;

    @InjectMocks
    private BookdropPeriodicScanTask task;

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
        assertThrows(APIException.class, () -> task.validatePermissions(user, request));
    }

    @Test
    void validatePermissions_shouldPass_whenUserCanAccessTaskManager() {
        user.getPermissions().setCanAccessTaskManager(true);
        assertDoesNotThrow(() -> task.validatePermissions(user, request));
    }

    @Test
    void execute_shouldTriggerBookdropRescan() {
        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskType.BOOKDROP_PERIODIC_SCANNING, response.getTaskType());
        assertEquals(TaskStatus.COMPLETED, response.getStatus());
        verify(bookdropMonitoringService).rescanBookdropFolder();
    }

    @Test
    void execute_shouldReturnFailed_whenRescanThrows() {
        doThrow(new RuntimeException("rescan failed")).when(bookdropMonitoringService).rescanBookdropFolder();

        TaskCreateResponse response = task.execute(request);

        assertEquals(TaskType.BOOKDROP_PERIODIC_SCANNING, response.getTaskType());
        assertEquals(TaskStatus.FAILED, response.getStatus());
        verify(bookdropMonitoringService).rescanBookdropFolder();
    }
}
