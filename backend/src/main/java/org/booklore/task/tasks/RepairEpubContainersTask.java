package org.booklore.task.tasks;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.websocket.TaskProgressPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.epub.EpubRepairService;
import org.booklore.service.epub.EpubRepairService.EpubRepairProgress;
import org.booklore.service.epub.EpubRepairService.EpubRepairResult;
import org.booklore.task.TaskCancellationManager;
import org.booklore.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class RepairEpubContainersTask implements Task {

    private final EpubRepairService epubRepairService;
    private final NotificationService notificationService;
    private final TaskCancellationManager cancellationManager;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (user.getPermissions() == null || !user.getPermissions().isCanAccessTaskManager()) {
            throw new APIException("You do not have permission to run this task", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        String taskId = request.getTaskId();
        sendProgress(taskId, 0, "Preparing to inspect EPUB containers", TaskStatus.IN_PROGRESS, null);

        try {
            EpubRepairResult result = epubRepairService.repairAll(
                    taskId,
                    () -> cancellationManager.isTaskCancelled(taskId),
                    progress -> sendProgress(
                            taskId,
                            getProgress(progress.processedCount(), progress.totalCount()),
                            formatProgress(progress),
                            TaskStatus.IN_PROGRESS,
                            progress.processedCount(),
                            progress.totalCount(),
                            progress.repairedCount() + progress.unchangedCount(),
                            progress.failedCount()));

            TaskStatus status = result.cancelled() ? TaskStatus.CANCELLED : TaskStatus.COMPLETED;
            String message = String.format(
                    "EPUB container repair %s: %d repaired, %d unchanged, %d failed",
                    result.cancelled() ? "cancelled" : "completed",
                    result.repairedCount(),
                    result.unchangedCount(),
                    result.failedCount());
            sendProgress(
                    taskId,
                    result.cancelled() ? getProgress(result.processedCount(), result.totalCount()) : 100,
                    message,
                    status,
                    result.processedCount(),
                    result.totalCount(),
                    result.repairedCount() + result.unchangedCount(),
                    result.failedCount());

            return TaskCreateResponse.builder()
                    .taskId(taskId)
                    .taskType(getTaskType())
                    .status(status)
                    .build();
        } catch (RuntimeException exception) {
            log.error("EPUB container repair task {} failed", taskId, exception);
            sendProgress(taskId, 0, "EPUB container repair failed: " + failureMessage(exception), TaskStatus.FAILED, null);
            throw exception;
        }
    }

    private int getProgress(int processedCount, long totalCount) {
        if (totalCount == 0) {
            return 100;
        }
        return Math.min(99, (int) (processedCount * 100L / totalCount));
    }

    private String formatProgress(EpubRepairProgress progress) {
        return String.format("Inspecting EPUBs: %d/%d (%d repaired, %d failed)",
                progress.processedCount(),
                progress.totalCount(),
                progress.repairedCount(),
                progress.failedCount());
    }

    private String failureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
    }

    private void sendProgress(
            String taskId,
            int progress,
            String message,
            TaskStatus status,
            Integer processedCount,
            Long totalCount,
            Integer repairedCount,
            Integer failedCount) {
        TaskProgressPayload.TaskProgressPayloadBuilder payload = TaskProgressPayload.builder()
                .taskId(taskId)
                .taskType(getTaskType())
                .message(message)
                .progress(progress)
                .taskStatus(status);
        if (processedCount != null) {
            payload.processedCount(processedCount)
                    .totalCount(totalCount == null ? null : Math.toIntExact(totalCount))
                    .succeededCount(repairedCount)
                    .failedCount(failedCount);
        }
        notificationService.sendMessage(Topic.TASK_PROGRESS, payload.build());
    }

    private void sendProgress(String taskId, int progress, String message, TaskStatus status, EpubRepairProgress update) {
        if (update == null) {
            sendProgress(taskId, progress, message, status, null, null, null, null);
            return;
        }
        sendProgress(
                taskId,
                progress,
                message,
                status,
                update.processedCount(),
                update.totalCount(),
                update.repairedCount() + update.unchangedCount(),
                update.failedCount());
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.REPAIR_EPUB_CONTAINERS;
    }
}
