package org.booklore.service.task;

import org.booklore.repository.TaskHistoryRepository;
import org.booklore.model.entity.TaskHistoryEntity;
import org.booklore.service.audit.AuditService;
import org.booklore.task.TaskStatus;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.enums.TaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskHistoryServiceTest {

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

    @Mock
    private TaskHistoryRepository taskHistoryRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TaskHistoryService taskHistoryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testCreateTask_savesEntity() {
        String taskId = "task1";
        TaskType type = TaskType.REFRESH_LIBRARY_METADATA;
        Long userId = 123L;
        Map<String, Object> options = new HashMap<>();
        options.put("key", "value");

        ArgumentCaptor<TaskHistoryEntity> captor = ArgumentCaptor.forClass(TaskHistoryEntity.class);

        taskHistoryService.createTask(taskId, type, userId, options);

        verify(taskHistoryRepository, times(1)).save(captor.capture());
        TaskHistoryEntity saved = captor.getValue();
        assertEquals(taskId, saved.getId());
        assertEquals(type, saved.getType());
        assertEquals(TaskStatus.ACCEPTED, saved.getStatus());
        assertEquals(userId, saved.getUserId());
        assertEquals(0, saved.getProgressPercentage());
        assertEquals(options, saved.getTaskOptions());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testUpdateTaskStatus_foundAndUpdated() {
        String taskId = "task2";
        TaskHistoryEntity entity = TaskHistoryEntity.builder()
                .id(taskId)
                .type(TaskType.SYNC_LIBRARY_FILES)
                .status(TaskStatus.ACCEPTED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();

        when(taskHistoryRepository.findById(taskId)).thenReturn(Optional.of(entity));

        taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Done");

        assertEquals(TaskStatus.COMPLETED, entity.getStatus());
        assertEquals("Done", entity.getMessage());
        assertEquals(100, entity.getProgressPercentage());
        assertNotNull(entity.getCompletedAt());
        assertNotNull(entity.getUpdatedAt());
        verify(taskHistoryRepository).save(entity);
    }

    @Test
    void testUpdateTaskStatus_notFound() {
        when(taskHistoryRepository.findById("notfound")).thenReturn(Optional.empty());
        taskHistoryService.updateTaskStatus("notfound", TaskStatus.FAILED, "Error");
        verify(taskHistoryRepository, never()).save(any());
    }

    @Test
    void testUpdateTaskError_foundAndUpdated() {
        String taskId = "task3";
        TaskHistoryEntity entity = TaskHistoryEntity.builder()
                .id(taskId)
                .type(TaskType.REFRESH_LIBRARY_METADATA)
                .status(TaskStatus.ACCEPTED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();

        when(taskHistoryRepository.findById(taskId)).thenReturn(Optional.of(entity));

        taskHistoryService.updateTaskError(taskId, "Some error");

        assertEquals(TaskStatus.FAILED, entity.getStatus());
        assertEquals("Some error", entity.getErrorDetails());
        assertEquals(0, entity.getProgressPercentage());
        assertNotNull(entity.getCompletedAt());
        assertNotNull(entity.getUpdatedAt());
        verify(taskHistoryRepository).save(entity);
    }

    @Test
    void testUpdateTaskError_notFound() {
        when(taskHistoryRepository.findById("notfound")).thenReturn(Optional.empty());
        taskHistoryService.updateTaskError("notfound", "Error");
        verify(taskHistoryRepository, never()).save(any());
    }

    @Test
    void testGetLatestTasksForEachType_success() {
        TaskHistoryEntity importTask = TaskHistoryEntity.builder()
                .id("t1")
                .type(TaskType.REFRESH_LIBRARY_METADATA)
                .status(TaskStatus.COMPLETED)
                .progressPercentage(100)
                .createdAt(FIXED_TIME)
                .build();

        TaskHistoryEntity exportTask = TaskHistoryEntity.builder()
                .id("t2")
                .type(TaskType.SYNC_LIBRARY_FILES)
                .status(TaskStatus.ACCEPTED)
                .progressPercentage(50)
                .createdAt(FIXED_TIME.plusMinutes(5))
                .build();

        when(taskHistoryRepository.findLatestTaskForEachType())
                .thenReturn(Arrays.asList(importTask, exportTask));

        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();

        assertNotNull(response);
        List<TasksHistoryResponse.TaskHistory> histories = response.getTaskHistories();
        assertTrue(histories.stream().anyMatch(h -> TaskType.REFRESH_LIBRARY_METADATA.equals(h.getType()) && "t1".equals(h.getId())));
        assertTrue(histories.stream().anyMatch(h -> TaskType.SYNC_LIBRARY_FILES.equals(h.getType()) && "t2".equals(h.getId())));
        assertFalse(histories.stream().anyMatch(h -> h.getType() != null && h.getType().isHiddenFromUI()));
        assertTrue(histories.stream().anyMatch(h -> h.getId() == null));
    }

    @Test
    void testGetLatestTasksForEachType_exceptionHandled() {
        when(taskHistoryRepository.findLatestTaskForEachType()).thenThrow(new RuntimeException("DB error"));
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().stream().allMatch(h -> h.getId() == null));
    }

    @Test
    void testGetLatestTasksForEachType_skipsInvalidType() {
        TaskHistoryEntity invalidTask = TaskHistoryEntity.builder()
                .id("t3")
                .type(null)
                .status(TaskStatus.FAILED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();

        when(taskHistoryRepository.findLatestTaskForEachType()).thenReturn(Collections.singletonList(invalidTask));

        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().stream().allMatch(h -> h.getId() == null || h.getType() != null));
    }

    @Test
    void testCreateTask_withNullOptions() {
        String taskId = "taskNullOptions";
        TaskType type = TaskType.CLEANUP_TEMP_METADATA;
        Long userId = 456L;

        ArgumentCaptor<TaskHistoryEntity> captor = ArgumentCaptor.forClass(TaskHistoryEntity.class);

        taskHistoryService.createTask(taskId, type, userId, null);

        verify(taskHistoryRepository, times(1)).save(captor.capture());
        TaskHistoryEntity saved = captor.getValue();
        assertNull(saved.getTaskOptions());
    }

    @Test
    void testUpdateTaskStatus_withNullMessage() {
        String taskId = "taskNullMsg";
        TaskHistoryEntity entity = TaskHistoryEntity.builder()
                .id(taskId)
                .type(TaskType.CLEANUP_TEMP_METADATA)
                .status(TaskStatus.ACCEPTED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();

        when(taskHistoryRepository.findById(taskId)).thenReturn(Optional.of(entity));

        taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, null);

        assertEquals(TaskStatus.COMPLETED, entity.getStatus());
        assertNull(entity.getMessage());
        assertEquals(100, entity.getProgressPercentage());
        assertNotNull(entity.getCompletedAt());
        verify(taskHistoryRepository).save(entity);
    }

    @Test
    void testUpdateTaskError_withNullErrorDetails() {
        String taskId = "taskNullError";
        TaskHistoryEntity entity = TaskHistoryEntity.builder()
                .id(taskId)
                .type(TaskType.CLEANUP_TEMP_METADATA)
                .status(TaskStatus.ACCEPTED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();

        when(taskHistoryRepository.findById(taskId)).thenReturn(Optional.of(entity));

        taskHistoryService.updateTaskError(taskId, null);

        assertEquals(TaskStatus.FAILED, entity.getStatus());
        assertNull(entity.getErrorDetails());
        assertEquals(0, entity.getProgressPercentage());
        assertNotNull(entity.getCompletedAt());
        verify(taskHistoryRepository).save(entity);
    }

    @Test
    void testCreateTask_withNullType() {
        String taskId = "taskNullType";
        Long userId = 789L;
        Map<String, Object> options = new HashMap<>();

        assertDoesNotThrow(() -> taskHistoryService.createTask(taskId, null, userId, options));
        ArgumentCaptor<TaskHistoryEntity> captor = ArgumentCaptor.forClass(TaskHistoryEntity.class);
        verify(taskHistoryRepository, times(1)).save(captor.capture());
        TaskHistoryEntity saved = captor.getValue();
        assertNull(saved.getType());
    }

    @Test
    void testGetLatestTasksForEachType_emptyRepository() {
        when(taskHistoryRepository.findLatestTaskForEachType()).thenReturn(Collections.emptyList());
        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().stream().allMatch(h -> h.getId() == null));
    }

    @Test
    void testGetLatestTasksForEachType_allTypesHidden() {
        List<TaskType> hiddenTypes = Arrays.asList(TaskType.values());
        TaskHistoryEntity dummyTask = TaskHistoryEntity.builder()
                .id("dummy")
                .type(TaskType.CLEANUP_DELETED_BOOKS)
                .status(TaskStatus.FAILED)
                .progressPercentage(0)
                .createdAt(FIXED_TIME)
                .build();
        when(taskHistoryRepository.findLatestTaskForEachType()).thenReturn(Collections.singletonList(dummyTask));

        hiddenTypes.forEach(type -> {
            try {
                java.lang.reflect.Field field = TaskType.class.getDeclaredField("hiddenFromUI");
                field.setAccessible(true);
                field.set(type, true);
            } catch (Exception ignored) {
            }
        });

        TasksHistoryResponse response = taskHistoryService.getLatestTasksForEachType();
        assertNotNull(response);
        assertTrue(response.getTaskHistories().isEmpty());
    }
}
