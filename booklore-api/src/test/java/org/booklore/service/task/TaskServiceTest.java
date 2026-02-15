package org.booklore.service.task;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.TaskInfo;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.CronConfig;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.task.TaskCancellationManager;
import org.booklore.task.TaskStatus;
import org.booklore.task.tasks.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    private AuthenticationService authenticationService;
    private TaskHistoryService taskHistoryService;
    private TaskCronService taskCronService;
    private TaskCancellationManager cancellationManager;
    private Executor taskExecutor;
    private ObjectMapper objectMapper;
    private TaskScheduler taskScheduler;
    private TaskService taskService;
    private Task mockTask;

    @BeforeEach
    void setUp() {
        authenticationService = mock(AuthenticationService.class);
        taskHistoryService = mock(TaskHistoryService.class);
        taskCronService = mock(TaskCronService.class);
        cancellationManager = mock(TaskCancellationManager.class);
        taskExecutor = mock(Executor.class);
        objectMapper = mock(ObjectMapper.class);
        taskScheduler = mock(TaskScheduler.class);

        mockTask = mock(Task.class);
        when(mockTask.getTaskType()).thenReturn(TaskType.CLEANUP_TEMP_METADATA);

        taskService = new TaskService(
                authenticationService,
                taskHistoryService,
                taskCronService,
                List.of(mockTask),
                cancellationManager,
                taskExecutor,
                objectMapper,
                taskScheduler
        );
    }

    @Test
    void testRunAsUserThrowsExceptionForNullRequest() {
        APIException ex = assertThrows(APIException.class, () -> taskService.runAsUser(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void testRunAsUserThrowsExceptionForNullTaskType() {
        TaskCreateRequest req = TaskCreateRequest.builder().triggeredByCron(false).build();
        APIException ex = assertThrows(APIException.class, () -> taskService.runAsUser(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    @Disabled
    void testGetAvailableTasksReturnsNonNull() {
        CronConfig cronConfig = CronConfig.builder()
                .taskType(TaskType.CLEANUP_TEMP_METADATA)
                .enabled(false)
                .build();
        when(taskCronService.getCronConfigOrDefault(any())).thenReturn(cronConfig);
        List<TaskInfo> tasks = taskService.getAvailableTasks();
        assertNotNull(tasks);
        assertTrue(tasks.stream().anyMatch(t -> t.getTaskType() == TaskType.CLEANUP_TEMP_METADATA));
    }

    @Test
    void testRunAsUserSyncTask() {
        BookLoreUser user = new BookLoreUser();
        user.setId(1L);
        user.setUsername("user1");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(mockTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(TaskType.CLEANUP_TEMP_METADATA).build());
        TaskCreateRequest req = TaskCreateRequest.builder().taskType(TaskType.CLEANUP_TEMP_METADATA).triggeredByCron(false).build();
        TaskCreateResponse resp = taskService.runAsUser(req);
        assertEquals(TaskType.CLEANUP_TEMP_METADATA, resp.getTaskType());
    }

    @Test
    void testExecuteTaskThrowsForUnknownTaskType() {
        TaskCreateRequest req = TaskCreateRequest.builder().taskType(TaskType.CLEANUP_DELETED_BOOKS).triggeredByCron(false).build();
        BookLoreUser user = new BookLoreUser();
        user.setId(1L);
        user.setUsername("user1");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        assertThrows(UnsupportedOperationException.class, () -> taskService.runAsUser(req));
    }

    @Test
    void testParallelTaskAllowsMultipleRuns() {
        TaskType parallelType = TaskType.CLEANUP_TEMP_METADATA;
        when(mockTask.getTaskType()).thenReturn(parallelType);
        when(mockTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(parallelType).build());
        BookLoreUser user = new BookLoreUser();
        user.setId(2L);
        user.setUsername("parallelUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCreateRequest req1 = TaskCreateRequest.builder().taskType(parallelType).triggeredByCron(false).build();
        TaskCreateRequest req2 = TaskCreateRequest.builder().taskType(parallelType).triggeredByCron(false).build();

        TaskCreateResponse resp1 = taskService.runAsUser(req1);
        TaskCreateResponse resp2 = taskService.runAsUser(req2);

        assertEquals(parallelType, resp1.getTaskType());
        assertEquals(parallelType, resp2.getTaskType());
    }

    @Test
    void testNonParallelTaskBlocksSecondRunAsUser() {
        TaskType nonParallelType = TaskType.REFRESH_LIBRARY_METADATA;
        Task nonParallelTask = mock(Task.class);
        when(nonParallelTask.getTaskType()).thenReturn(nonParallelType);
        when(nonParallelTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(nonParallelType).build());

        taskService = new TaskService(
                authenticationService,
                taskHistoryService,
                taskCronService,
                List.of(nonParallelTask),
                cancellationManager,
                taskExecutor,
                objectMapper,
                taskScheduler
        );

        BookLoreUser user = new BookLoreUser();
        user.setId(3L);
        user.setUsername("nonParallelUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCreateRequest req = TaskCreateRequest.builder().taskType(nonParallelType).triggeredByCron(false).build();
        taskService.runAsUser(req);

        APIException ex = assertThrows(APIException.class, () -> taskService.runAsUser(req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void testCancelNonExistentTaskThrowsException() {
        BookLoreUser user = new BookLoreUser();
        user.setId(4L);
        user.setUsername("cancelUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        String fakeTaskId = "not-running-task-id";
        assertThrows(APIException.class, () -> taskService.cancelTask(fakeTaskId));
    }

    @Test
    void testAsyncTaskReturnsAcceptedStatus() {
        TaskType asyncType = TaskType.UPDATE_BOOK_RECOMMENDATIONS;
        Task asyncTask = mock(Task.class);
        when(asyncTask.getTaskType()).thenReturn(asyncType);
        when(asyncTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(asyncType).build());

        taskService = new TaskService(
                authenticationService,
                taskHistoryService,
                taskCronService,
                List.of(asyncTask),
                cancellationManager,
                taskExecutor,
                objectMapper,
                taskScheduler
        );

        BookLoreUser user = new BookLoreUser();
        user.setId(5L);
        user.setUsername("asyncUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCreateRequest req = TaskCreateRequest.builder().taskType(asyncType).triggeredByCron(false).build();
        TaskCreateResponse resp = taskService.runAsUser(req);

        assertEquals(asyncType, resp.getTaskType());
        assertEquals(TaskStatus.ACCEPTED, resp.getStatus());
    }

    @Test
    void testNullOptionsHandledGracefully() {
        TaskType type = TaskType.CLEANUP_TEMP_METADATA;
        when(mockTask.getTaskType()).thenReturn(type);
        when(mockTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(type).build());
        BookLoreUser user = new BookLoreUser();
        user.setId(6L);
        user.setUsername("nullOptionsUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCreateRequest req = TaskCreateRequest.builder().taskType(type).options(null).triggeredByCron(false).build();
        TaskCreateResponse resp = taskService.runAsUser(req);
        assertEquals(type, resp.getTaskType());
    }

    @Test
    void testExceptionInTaskExecutionPropagates() {
        TaskType type = TaskType.CLEANUP_TEMP_METADATA;
        when(mockTask.getTaskType()).thenReturn(type);
        when(mockTask.execute(any())).thenThrow(new RuntimeException("Task failed"));
        BookLoreUser user = new BookLoreUser();
        user.setId(7L);
        user.setUsername("exceptionUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        TaskCreateRequest req = TaskCreateRequest.builder().taskType(type).triggeredByCron(false).build();
        assertThrows(RuntimeException.class, () -> taskService.runAsUser(req));
    }

    @Test
    void testRunAsUserThrowsExceptionForNullUser() {
        when(authenticationService.getAuthenticatedUser()).thenReturn(null);
        TaskCreateRequest req = TaskCreateRequest.builder().taskType(TaskType.CLEANUP_TEMP_METADATA).triggeredByCron(false).build();
        assertThrows(NullPointerException.class, () -> taskService.runAsUser(req));
    }

    @Test
    void testExecuteTaskThrowsForMissingTaskInRegistry() {
        TaskCreateRequest req = TaskCreateRequest.builder().taskType(TaskType.CLEANUP_DELETED_BOOKS).triggeredByCron(false).build();
        BookLoreUser user = new BookLoreUser();
        user.setId(8L);
        user.setUsername("missingTaskUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        assertThrows(UnsupportedOperationException.class, () -> taskService.runAsUser(req));
    }

    @Test
    void testConvertOptionsToMapHandlesInvalidType() {
        BookLoreUser user = new BookLoreUser();
        user.setId(9L);
        user.setUsername("invalidOptionsUser");
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenThrow(new IllegalArgumentException("Conversion failed"));
        TaskCreateRequest req = TaskCreateRequest.builder().taskType(TaskType.CLEANUP_TEMP_METADATA).options(new Object()).triggeredByCron(false).build();
        when(mockTask.execute(any())).thenReturn(TaskCreateResponse.builder().taskType(TaskType.CLEANUP_TEMP_METADATA).build());
        TaskCreateResponse resp = taskService.runAsUser(req);
        assertEquals(TaskType.CLEANUP_TEMP_METADATA, resp.getTaskType());
    }

    @Test
    void testInitializeScheduledTasksDoesNotThrow() {
        when(taskCronService.getAllEnabledCronConfigs()).thenReturn(List.of());
        assertDoesNotThrow(() -> taskService.initializeScheduledTasks());
    }

    @Test
    void testRescheduleTaskDoesNotThrowWhenEnabled() {
        CronConfig cronConfig = CronConfig.builder()
                .taskType(TaskType.CLEANUP_TEMP_METADATA)
                .enabled(true)
                .cronExpression("0 0 0 1 1 0")
                .build();
        when(taskCronService.getCronConfigOrDefault(TaskType.CLEANUP_TEMP_METADATA)).thenReturn(cronConfig);
        assertDoesNotThrow(() -> taskService.rescheduleTask(TaskType.CLEANUP_TEMP_METADATA));
    }
}
