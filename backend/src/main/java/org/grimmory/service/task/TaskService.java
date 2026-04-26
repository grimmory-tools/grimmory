package org.grimmory.service.task;

import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.TaskInfo;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCancelResponse;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.entity.TaskCronConfigurationEntity;
import org.booklore.model.enums.TaskType;
import org.booklore.task.TaskCancellationManager;
import org.booklore.task.TaskStatus;
import org.booklore.task.tasks.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TaskService {

    private final AuthenticationService authenticationService;
    private final TaskHistoryService taskHistoryService;
    private final TaskCronService taskCronService;
    private final Map<TaskType, Task> taskRegistry;
    private final ConcurrentMap<TaskType, String> runningTasks = new ConcurrentHashMap<>();
    private final TaskCancellationManager cancellationManager;
    private final Executor taskExecutor;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final Map<TaskType, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public TaskService(
            AuthenticationService authenticationService,
            TaskHistoryService taskHistoryService,
            @Lazy TaskCronService taskCronService,
            List<Task> tasks,
            TaskCancellationManager cancellationManager,
            Executor taskExecutor,
            ObjectMapper objectMapper,
            TaskScheduler taskScheduler) {
        this.authenticationService = authenticationService;
        this.taskHistoryService = taskHistoryService;
        this.taskCronService = taskCronService;
        this.taskRegistry = tasks.stream().collect(Collectors.toMap(Task::getTaskType, Function.identity()));
        this.cancellationManager = cancellationManager;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
    }

    public void initializeScheduledTasks() {
        List<TaskCronConfigurationEntity> enabledConfigs = taskCronService.getAllEnabledCronConfigs();
        log.info("Initializing {} scheduled tasks", enabledConfigs.size());
        enabledConfigs.forEach(this::scheduleTask);
    }

    public void rescheduleTask(TaskType taskType) {
        cancelScheduledTask(taskType);
        taskCronService.getCronConfigOrDefault(taskType);
        var cronConfig = taskCronService.getCronConfigOrDefault(taskType);

        if (cronConfig.getEnabled() != null && cronConfig.getEnabled() && cronConfig.getCronExpression() != null) {
            TaskCronConfigurationEntity config = TaskCronConfigurationEntity.builder()
                    .taskType(taskType)
                    .cronExpression(cronConfig.getCronExpression())
                    .enabled(cronConfig.getEnabled())
                    .build();
            scheduleTask(config);
        }
    }

    private void scheduleTask(TaskCronConfigurationEntity config) {
        cancelScheduledTask(config.getTaskType());

        try {
            CronTrigger trigger = new CronTrigger(config.getCronExpression());
            ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                    () -> executeCronTask(config.getTaskType()),
                    trigger
            );

            scheduledTasks.put(config.getTaskType(), scheduledTask);
            log.info("Scheduled task {} with cron expression: {}", config.getTaskType(), config.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule task {}", config.getTaskType(), e);
        }
    }

    private void cancelScheduledTask(TaskType taskType) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.remove(taskType);
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            log.info("Cancelled scheduled task: {}", taskType);
        }
    }

    public List<TaskInfo> getAvailableTasks() {
        return Arrays.stream(TaskType.values())
                .filter(taskType -> !taskType.isHiddenFromUI())
                .map(taskType -> {
                    TaskInfo metadata = TaskInfo.fromTaskType(taskType);
                    Task task = taskRegistry.get(taskType);
                    if (task != null) {
                        metadata.setMetadata(task.getMetadata());
                    }
                    if (taskType.isCronSupported()) {
                        var cronConfig = taskCronService.getCronConfigOrDefault(taskType);
                        metadata.setCronConfig(cronConfig);
                    }
                    return metadata;
                })
                .collect(Collectors.toList());
    }

    public void executeCronTask(TaskType taskType) {
        log.info("Executing cron-scheduled task: {}", taskType);
        try {
            BookLoreUser systemUser = authenticationService.getSystemUser();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(systemUser, null, List.of());
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            TaskCreateRequest request = TaskCreateRequest.builder()
                    .taskType(taskType)
                    .triggeredByCron(true)
                    .build();

            runAsSystemUser(request);
        } catch (Exception e) {
            log.error("Failed to execute cron-scheduled task: {}", taskType, e);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public TaskCreateResponse runAsUser(TaskCreateRequest request) {
        if (request == null || request.getTaskType() == null) {
            throw new APIException("Task request and task type cannot be null", HttpStatus.BAD_REQUEST);
        }
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        TaskType taskType = request.getTaskType();
        if (taskType.isAsync()) {
            return runAsync(request, user, taskType);
        } else {
            return runSync(request, user, taskType);
        }
    }

    private void runAsSystemUser(TaskCreateRequest request) {
        if (request == null || request.getTaskType() == null) {
            throw new APIException("Task request and task type cannot be null", HttpStatus.BAD_REQUEST);
        }
        BookLoreUser systemUser = authenticationService.getSystemUser();
        TaskType taskType = request.getTaskType();
        if (taskType.isAsync()) {
            runAsync(request, systemUser, taskType);
        } else {
            runSync(request, systemUser, taskType);
        }
    }

    private TaskCreateResponse runAsync(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        String taskId = initializeTask(request, user, taskType);
        TaskCreateResponse response = TaskCreateResponse.builder()
                .taskId(taskId)
                .taskType(taskType)
                .status(TaskStatus.ACCEPTED)
                .build();
        taskExecutor.execute(() ->
                        executeAsyncTask(taskId, request, taskType)
        );
        return response;
    }

    public TaskCancelResponse cancelTask(String taskId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isRunning = runningTasks.containsValue(taskId);
        if (!isRunning) {
            throw new APIException("Task not found or not running: " + taskId, HttpStatus.NOT_FOUND);
        }
        cancellationManager.cancelTask(taskId);
        taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task cancellation requested by user");
        log.info("Task {} cancellation requested by user {}", taskId, user.getUsername());
        return TaskCancelResponse.builder()
                .taskId(taskId)
                .cancelled(true)
                .message("Task cancellation requested. The task will stop at the next checkpoint.")
                .build();
    }

    private void executeAsyncTask(String taskId, TaskCreateRequest request, TaskType taskType) {
        try {
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, "Task execution started");
            request.setTaskId(taskId);
            if (cancellationManager.isTaskCancelled(taskId)) {
                log.info("Task {} was cancelled before execution", taskId);
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task was cancelled");
                return;
            }
            executeTask(request);
            if (cancellationManager.isTaskCancelled(taskId)) {
                log.info("Task {} was cancelled during execution", taskId);
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.CANCELLED, "Task was cancelled");
            } else {
                taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Task completed successfully");
            }
        } catch (Exception e) {
            log.error("Async task {} of type {} failed", taskId, taskType, e);
            taskHistoryService.updateTaskError(taskId, e.getMessage());
        } finally {
            if (!taskType.isParallel()) {
                runningTasks.remove(taskType);
            }
            cancellationManager.clearCancellation(taskId);
        }
    }

    private TaskCreateResponse runSync(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        String taskId = initializeTask(request, user, taskType);
        try {
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.IN_PROGRESS, "Task execution started");
            request.setTaskId(taskId);
            TaskCreateResponse response = executeTask(request);
            response.setTaskId(taskId);
            taskHistoryService.updateTaskStatus(taskId, TaskStatus.COMPLETED, "Task completed successfully");
            return response;
        } catch (Exception e) {
            log.error("Sync task {} of type {} failed", taskId, taskType, e);
            taskHistoryService.updateTaskError(taskId, e.getMessage());
            throw e;
        } finally {
            if (!taskType.isParallel()) {
                runningTasks.remove(taskType);
            }
        }
    }

    private String initializeTask(TaskCreateRequest request, BookLoreUser user, TaskType taskType) {
        Task task = taskRegistry.get(taskType);
        if (task != null) {
            task.validatePermissions(user, request);
        }

        if (!taskType.isParallel()) {
            String existingTaskId = runningTasks.putIfAbsent(taskType, "");
            if (existingTaskId != null) {
                log.warn("Task of type {} is already running, rejecting new request", taskType);
                throw new APIException("A task of type " + taskType + " is already running. Please wait for it to complete.", HttpStatus.CONFLICT);
            }
        }
        String taskId = UUID.randomUUID().toString();
        if (!taskType.isParallel()) {
            runningTasks.put(taskType, taskId);
        }
        Map<String, Object> options = convertOptionsToMap(request.getOptions());
        taskHistoryService.createTask(taskId, taskType, user.getId(), options);
        return taskId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertOptionsToMap(Object options) {
        if (options == null) {
            return Map.of();
        }
        if (options instanceof Map) {
            return (Map<String, Object>) options;
        }
        try {
            return objectMapper.convertValue(options, Map.class);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to convert options to map, using empty map", e);
            return Map.of();
        }
    }

    private TaskCreateResponse executeTask(TaskCreateRequest request) {
        TaskType taskType = request.getTaskType();
        log.info("{}: Executing task", taskType);
        Task task = taskRegistry.get(taskType);
        if (task == null) {
            throw new UnsupportedOperationException("Task type not implemented: " + taskType);
        }
        return task.execute(request);
    }
}
