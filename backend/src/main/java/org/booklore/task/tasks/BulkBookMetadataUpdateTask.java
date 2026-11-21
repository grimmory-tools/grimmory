package org.booklore.task.tasks;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BulkMetadataUpdateProgress;
import org.booklore.model.dto.BulkMetadataUpdateResult;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.websocket.TaskProgressPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.task.TaskCancellationManager;
import org.booklore.task.TaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.booklore.exception.ApiError.PERMISSION_DENIED;
import static org.booklore.model.enums.UserPermission.CAN_BULK_EDIT_METADATA;

@AllArgsConstructor
@Component
@Slf4j
public class BulkBookMetadataUpdateTask implements Task {

    private final BookMetadataService bookMetadataService;
    private final NotificationService notificationService;
    private final TaskCancellationManager cancellationManager;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        BulkMetadataUpdateRequest updateRequest = request.getOptionsAs(BulkMetadataUpdateRequest.class);
        if (updateRequest == null || updateRequest.getBookIds() == null) {
            throw new APIException("Book IDs are required for bulk metadata updates", HttpStatus.BAD_REQUEST);
        }
        if (updateRequest.getBookIds().stream().anyMatch(bookId -> bookId == null)) {
            throw new APIException("Book IDs cannot contain null values", HttpStatus.BAD_REQUEST);
        }
        if (user.getPermissions() == null
                || (!user.getPermissions().isAdmin() && !CAN_BULK_EDIT_METADATA.isGranted(user.getPermissions()))) {
            throw PERMISSION_DENIED.createException(CAN_BULK_EDIT_METADATA);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        BulkMetadataUpdateRequest updateRequest = request.getOptionsAs(BulkMetadataUpdateRequest.class);
        String taskId = request.getTaskId();
        int totalCount = updateRequest.getBookIds().size();
        AtomicReference<BulkMetadataUpdateProgress> lastProgress = new AtomicReference<>();

        sendProgress(taskId, 0, "Starting metadata update", TaskStatus.IN_PROGRESS, null);
        try {
            BulkMetadataUpdateResult result = bookMetadataService.bulkUpdateMetadata(
                    updateRequest,
                    updateRequest.isMergeCategories(),
                    updateRequest.isMergeMoods(),
                    updateRequest.isMergeTags(),
                    progress -> {
                        lastProgress.set(progress);
                        if (cancellationManager.isTaskCancelled(taskId)) {
                            throw new CancellationException("Bulk metadata update was cancelled");
                        }
                        sendProgress(taskId, getProgress(progress), formatProgress(progress), TaskStatus.IN_PROGRESS, progress);
                    });

            String message = String.format(
                    "Updated metadata for %d of %d books; %d failed",
                    result.succeededCount(),
                    result.totalCount(),
                    result.failedCount());
            sendProgress(taskId, 100, message, TaskStatus.COMPLETED, new BulkMetadataUpdateProgress(
                    result.processedCount(),
                    result.totalCount(),
                    result.succeededCount(),
                    result.failedCount(),
                    List.of()));

            return TaskCreateResponse.builder()
                    .taskId(taskId)
                    .taskType(getTaskType())
                    .status(TaskStatus.COMPLETED)
                    .build();
        } catch (CancellationException exception) {
            BulkMetadataUpdateProgress progress = lastProgress.get();
            if (progress == null) {
                progress = new BulkMetadataUpdateProgress(0, totalCount, 0, 0, List.of());
            } else {
                progress = new BulkMetadataUpdateProgress(
                        progress.processedCount(),
                        progress.totalCount(),
                        progress.succeededCount(),
                        progress.failedCount(),
                        progress.failedBooks());
            }
            sendProgress(taskId, getProgress(progress), "Cancellation requested; stopping after the current batch", TaskStatus.CANCELLED, progress);
            return TaskCreateResponse.builder()
                    .taskId(taskId)
                    .taskType(getTaskType())
                    .status(TaskStatus.CANCELLED)
                    .build();
        } catch (RuntimeException exception) {
            log.error("Bulk metadata update task {} failed", taskId, exception);
            sendProgress(taskId, 0, "Bulk metadata update failed: " + getFailureMessage(exception), TaskStatus.FAILED, null);
            throw exception;
        }
    }

    private int getProgress(BulkMetadataUpdateProgress progress) {
        if (progress.totalCount() == 0) {
            return 100;
        }
        return Math.min(99, progress.processedCount() * 100 / progress.totalCount());
    }

    private String formatProgress(BulkMetadataUpdateProgress progress) {
        return String.format("Updating metadata: %d/%d books", progress.processedCount(), progress.totalCount());
    }

    private String getFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
    }

    private void sendProgress(
            String taskId,
            int progress,
            String message,
            TaskStatus status,
            BulkMetadataUpdateProgress updateProgress) {
        TaskProgressPayload.TaskProgressPayloadBuilder payload = TaskProgressPayload.builder()
                .taskId(taskId)
                .taskType(getTaskType())
                .message(message)
                .progress(progress)
                .taskStatus(status);
        if (updateProgress != null) {
            payload.processedCount(updateProgress.processedCount())
                    .totalCount(updateProgress.totalCount())
                    .succeededCount(updateProgress.succeededCount())
                    .failedCount(updateProgress.failedCount())
                    .failedBooks(updateProgress.failedBooks());
        }
        notificationService.sendMessage(Topic.TASK_PROGRESS, payload.build());
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.BULK_UPDATE_BOOK_METADATA;
    }
}
