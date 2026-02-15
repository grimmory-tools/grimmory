package org.booklore.service.task;

import org.booklore.repository.TaskHistoryRepository;
import org.booklore.model.entity.TaskHistoryEntity;
import org.booklore.task.TaskStatus;
import org.booklore.model.dto.response.TasksHistoryResponse;
import org.booklore.model.enums.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskHistoryService {

    private final TaskHistoryRepository taskHistoryRepository;
    private final AuditService auditService;

    @Transactional
    public void createTask(String taskId, TaskType type, Long userId, Map<String, Object> options) {
        TaskHistoryEntity task = TaskHistoryEntity.builder()
                .id(taskId)
                .type(type)
                .status(TaskStatus.ACCEPTED)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .progressPercentage(0)
                .taskOptions(options)
                .build();
        taskHistoryRepository.save(task);
        auditService.log(AuditAction.TASK_EXECUTED, "Task", null, buildTaskDescription(type, options));
    }

    private String buildTaskDescription(TaskType type, Map<String, Object> options) {
        String taskName = type != null ? type.getName() : "Unknown";
        StringBuilder sb = new StringBuilder("Started task: ").append(taskName);
        if (options == null || options.isEmpty()) {
            return sb.toString();
        }
        Object bookIds = options.get("bookIds");
        Object libraryId = options.get("libraryId");
        if (bookIds instanceof Collection<?> ids && !ids.isEmpty()) {
            sb.append(" (Book IDs: ").append(ids.stream().map(Object::toString).collect(Collectors.joining(", "))).append(")");
        } else if (libraryId != null) {
            sb.append(" (Library ID: ").append(libraryId).append(")");
        }
        return sb.toString();
    }

    @Transactional
    public void updateTaskStatus(String taskId, TaskStatus status, String message) {
        taskHistoryRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setMessage(message);
            task.setUpdatedAt(LocalDateTime.now());

            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                task.setCompletedAt(LocalDateTime.now());
                task.setProgressPercentage(100);
            }

            taskHistoryRepository.save(task);
        });
    }

    @Transactional
    public void updateTaskError(String taskId, String errorDetails) {
        taskHistoryRepository.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorDetails(errorDetails);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskHistoryRepository.save(task);
            log.error("Task failed: id={}", taskId);
        });
    }

    @Transactional(readOnly = true)
    public TasksHistoryResponse getLatestTasksForEachType() {
        List<TaskHistoryEntity> latestTasks;
        try {
            latestTasks = taskHistoryRepository.findLatestTaskForEachType();
        } catch (Exception e) {
            log.warn("Error fetching latest tasks, possibly due to removed enum values: {}", e.getMessage());
            latestTasks = Collections.emptyList();
        }

        Map<TaskType, TaskHistoryEntity> taskHistoryMap = latestTasks.stream()
                .filter(task -> {
                    try {
                        return task.getType() != null;
                    } catch (Exception e) {
                        log.warn("Skipping task with invalid type: taskId={}", task.getId());
                        return false;
                    }
                })
                .collect(Collectors.toMap(TaskHistoryEntity::getType, task -> task, (existing, replacement) -> existing));

        List<TasksHistoryResponse.TaskHistory> allTasks = new ArrayList<>();

        for (TaskType taskType : TaskType.values()) {
            if (taskType.isHiddenFromUI()) {
                continue;
            }

            TaskHistoryEntity existingTask = taskHistoryMap.get(taskType);

            if (existingTask != null) {
                allTasks.add(mapToTaskInfo(existingTask));
            } else {
                allTasks.add(createMetadataOnlyTaskInfo(taskType));
            }
        }

        return TasksHistoryResponse.builder()
                .taskHistories(allTasks)
                .build();
    }

    private TasksHistoryResponse.TaskHistory mapToTaskInfo(TaskHistoryEntity task) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(task.getId())
                .type(task.getType())
                .status(task.getStatus())
                .progressPercentage(task.getProgressPercentage())
                .message(task.getMessage())
                .createdAt(toUtcInstant(task.getCreatedAt()))
                .updatedAt(toUtcInstant(task.getUpdatedAt()))
                .completedAt(toUtcInstant(task.getCompletedAt()))
                .build();
    }

    private Instant toUtcInstant(LocalDateTime localDateTime) {
        return localDateTime != null ? localDateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    private TasksHistoryResponse.TaskHistory createMetadataOnlyTaskInfo(TaskType taskType) {
        return TasksHistoryResponse.TaskHistory.builder()
                .id(null)
                .type(taskType)
                .status(null)
                .progressPercentage(null)
                .message(null)
                .createdAt(null)
                .updatedAt(null)
                .completedAt(null)
                .build();
    }
}
