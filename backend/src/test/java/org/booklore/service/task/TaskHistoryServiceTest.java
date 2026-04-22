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
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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

    // --- buildTaskDescription tests (via createTask audit log capture) ---

    private String captureAuditDescription(TaskType type, Map<String, Object> options) {
        taskHistoryService.createTask("test-task", type, 1L, options);
        ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(any(), any(), any(), descCaptor.capture());
        reset(auditService);
        return descCaptor.getValue();
    }

    @Test
    void testBuildTaskDescription_withSmallBookIdsList() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(10L, 20L, 30L));

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.startsWith("Started task:"));
        assertTrue(desc.contains("3 books, IDs:"));
        assertTrue(desc.contains("10"));
        assertTrue(desc.contains("20"));
        assertTrue(desc.contains("30"));
        assertTrue(desc.endsWith(")"));
        assertFalse(desc.contains("..."));
    }

    @Test
    void testBuildTaskDescription_withSingleBookId() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(42L));

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.contains("1 books, IDs: 42)"));
    }

    @Test
    void testBuildTaskDescription_withLibraryId() {
        Map<String, Object> options = new HashMap<>();
        options.put("libraryId", 7L);

        String desc = captureAuditDescription(TaskType.REFRESH_LIBRARY_METADATA, options);

        assertTrue(desc.contains("Library ID: 7"));
    }

    @Test
    void testBuildTaskDescription_withEmptyBookIds() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Collections.emptySet());

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertFalse(desc.contains("books"));
        assertFalse(desc.contains("Library ID"));
    }

    @Test
    void testBuildTaskDescription_withNullOptions() {
        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, null);

        assertEquals("Started task: Refresh Metadata", desc);
    }

    @Test
    void testBuildTaskDescription_withEmptyOptions() {
        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, new HashMap<>());

        assertEquals("Started task: Refresh Metadata", desc);
    }

    @Test
    void testBuildTaskDescription_withNullType() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(1L));

        String desc = captureAuditDescription(null, options);

        assertTrue(desc.startsWith("Started task: Unknown"));
    }

    @Test
    void testBuildTaskDescription_bookIdsPrefersOverLibraryId() {
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", Set.of(1L, 2L));
        options.put("libraryId", 5L);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.contains("2 books, IDs:"));
        assertFalse(desc.contains("Library ID"));
    }

    @Test
    void testBuildTaskDescription_largeBookIdsListTruncated() {
        Set<Long> ids = LongStream.rangeClosed(1, 500)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", ids);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.length() <= 1024, "Description length " + desc.length() + " exceeds 1024");
        assertTrue(desc.contains("500 books, IDs:"));
        assertTrue(desc.endsWith("...)"));
    }

    @Test
    void testBuildTaskDescription_veryLargeBookIdsListStaysWithinLimit() {
        Set<Long> ids = LongStream.rangeClosed(1, 2000)
                .boxed()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Object> options = new HashMap<>();
        options.put("bookIds", ids);

        String desc = captureAuditDescription(TaskType.REFRESH_METADATA_MANUAL, options);

        assertTrue(desc.length() <= 1024, "Description length " + desc.length() + " exceeds 1024");
        assertTrue(desc.contains("2000 books, IDs:"));
        assertTrue(desc.endsWith("...)"));
    }
}
