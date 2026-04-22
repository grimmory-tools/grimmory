package org.booklore.service.task;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCronConfigRequest;
import org.booklore.model.dto.response.CronConfig;
import org.booklore.model.entity.TaskCronConfigurationEntity;
import org.booklore.model.enums.TaskType;
import org.booklore.repository.TaskCronConfigurationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskCronServiceTest {

    @Mock
    private TaskCronConfigurationRepository repository;

    @Mock
    private AuthenticationService authService;

    @InjectMocks
    private TaskCronService service;

    private AutoCloseable mocks;

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

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

    private TaskCronConfigurationEntity buildEntity(TaskType type, String cron, boolean enabled) {
        return TaskCronConfigurationEntity.builder()
                .id(1L)
                .taskType(type)
                .cronExpression(cron)
                .enabled(enabled)
                .createdBy(10L)
                .createdAt(FIXED_TIME)
                .updatedAt(FIXED_TIME)
                .build();
    }

    @Test
    void testGetAllEnabledCronConfigs_returnsList() {
        List<TaskCronConfigurationEntity> configs = List.of(
                buildEntity(TaskType.CLEANUP_DELETED_BOOKS, "0 0 1 * * *", true)
        );
        when(repository.findByEnabledTrue()).thenReturn(configs);

        List<TaskCronConfigurationEntity> result = service.getAllEnabledCronConfigs();
        assertEquals(1, result.size());
        assertEquals(TaskType.CLEANUP_DELETED_BOOKS, result.getFirst().getTaskType());
    }

    @Test
    void testGetCronConfigOrDefault_existingConfig() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        TaskCronConfigurationEntity entity = buildEntity(type, "0 0 1 * * *", true);
        when(repository.findByTaskType(type)).thenReturn(Optional.of(entity));

        CronConfig config = service.getCronConfigOrDefault(type);
        assertEquals(type, config.getTaskType());
        assertEquals("0 0 1 * * *", config.getCronExpression());
        assertTrue(config.getEnabled());
    }

    @Test
    void testGetCronConfigOrDefault_noConfig_returnsDefault() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        when(repository.findByTaskType(type)).thenReturn(Optional.empty());

        CronConfig config = service.getCronConfigOrDefault(type);
        assertEquals(type, config.getTaskType());
        assertFalse(config.getEnabled());
        assertNull(config.getCronExpression());
    }

    @Test
    void testGetCronConfigOrDefault_invalidTaskType_throws() {
        TaskType type = mock(TaskType.class);
        when(type.isCronSupported()).thenReturn(false);

        APIException ex = assertThrows(APIException.class, () -> service.getCronConfigOrDefault(type));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("does not support cron scheduling"));
    }

    @Test
    void testGetCronConfigOrDefault_nullTaskType_throws() {
        APIException ex = assertThrows(APIException.class, () -> service.getCronConfigOrDefault(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Task type is required"));
    }

    @Test
    void testPatchCronConfig_updateExisting() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        BookLoreUser user = BookLoreUser.builder().id(10L).isDefaultPassword(false).build();
        TaskCronConfigurationEntity entity = buildEntity(type, "0 0 1 * * *", false);

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByTaskType(type)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskCronConfigRequest req = new TaskCronConfigRequest();
        req.setCronExpression("0 0 2 * * *");
        req.setEnabled(true);

        CronConfig config = service.patchCronConfig(type, req);
        assertEquals("0 0 2 * * *", config.getCronExpression());
        assertTrue(config.getEnabled());
    }

    @Test
    void testPatchCronConfig_createNew() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        BookLoreUser user = BookLoreUser.builder().id(10L).isDefaultPassword(false).build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByTaskType(type)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskCronConfigRequest req = new TaskCronConfigRequest();
        req.setCronExpression("0 0 3 * * *");
        req.setEnabled(true);

        CronConfig config = service.patchCronConfig(type, req);
        assertEquals("0 0 3 * * *", config.getCronExpression());
        assertTrue(config.getEnabled());
        assertEquals(type, config.getTaskType());
    }

    @Test
    void testPatchCronConfig_invalidCronExpression_throws() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        BookLoreUser user = BookLoreUser.builder().id(10L).isDefaultPassword(false).build();

        when(authService.getAuthenticatedUser()).thenReturn(user);
        when(repository.findByTaskType(type)).thenReturn(Optional.empty());

        TaskCronConfigRequest req = new TaskCronConfigRequest();
        req.setCronExpression("invalid cron");
        req.setEnabled(true);

        APIException ex = assertThrows(APIException.class, () -> service.patchCronConfig(type, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid cron expression format"));
    }

    @Test
    void testPatchCronConfig_invalidTaskType_throws() {
        TaskType type = mock(TaskType.class);
        when(type.isCronSupported()).thenReturn(false);

        TaskCronConfigRequest req = new TaskCronConfigRequest();
        req.setCronExpression("0 0 1 * * *");
        req.setEnabled(true);

        APIException ex = assertThrows(APIException.class, () -> service.patchCronConfig(type, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("does not support cron scheduling"));
    }

    @Test
    void testValidateCronExpression_valid() {
        String cron = "0 0 1 * * *";
        assertDoesNotThrow(() -> {
            var method = TaskCronService.class.getDeclaredMethod("validateCronExpression", String.class);
            method.setAccessible(true);
            method.invoke(service, cron);
        });
    }

    @Test
    void testValidateCronExpression_invalidFieldCount() {
        String cron = "0 0 1 * *";
        Exception ex = assertThrows(Exception.class, () -> {
            var method = TaskCronService.class.getDeclaredMethod("validateCronExpression", String.class);
            method.setAccessible(true);
            method.invoke(service, cron);
        });
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(APIException.class, cause);
        assertTrue(cause.getMessage().contains("Expected 6 fields"));
    }

    @Test
    void testValidateCronExpression_invalidFormat() {
        String cron = "invalid cron expression";
        Exception ex = assertThrows(Exception.class, () -> {
            var method = TaskCronService.class.getDeclaredMethod("validateCronExpression", String.class);
            method.setAccessible(true);
            method.invoke(service, cron);
        });
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(APIException.class, cause);
        assertTrue(cause.getMessage().contains("Invalid cron expression format"));
    }

    @Test
    void testValidateTaskTypeForCron_supported() {
        TaskType type = TaskType.CLEANUP_DELETED_BOOKS;
        assertDoesNotThrow(() -> {
            var method = TaskCronService.class.getDeclaredMethod("validateTaskTypeForCron", TaskType.class);
            method.setAccessible(true);
            method.invoke(service, type);
        });
    }

    @Test
    void testValidateTaskTypeForCron_notSupported() {
        TaskType type = mock(TaskType.class);
        when(type.isCronSupported()).thenReturn(false);

        Exception ex = assertThrows(Exception.class, () -> {
            var method = TaskCronService.class.getDeclaredMethod("validateTaskTypeForCron", TaskType.class);
            method.setAccessible(true);
            method.invoke(service, type);
        });
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(APIException.class, cause);
        assertTrue(cause.getMessage().contains("does not support cron scheduling"));
    }

    @Test
    void testValidateTaskTypeForCron_nullType() {
        Exception ex = assertThrows(Exception.class, () -> {
            var method = TaskCronService.class.getDeclaredMethod("validateTaskTypeForCron", TaskType.class);
            method.setAccessible(true);
            method.invoke(service, (Object) null);
        });
        Throwable cause = ex.getCause();
        assertNotNull(cause);
        assertInstanceOf(APIException.class, cause);
        assertTrue(cause.getMessage().contains("Task type is required"));
    }
}
