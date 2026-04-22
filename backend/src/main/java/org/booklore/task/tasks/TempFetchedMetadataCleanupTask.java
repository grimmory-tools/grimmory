package org.booklore.task.tasks;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.MetadataFetchJobRepository;
import org.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TempFetchedMetadataCleanupTask implements Task {

    private final MetadataFetchJobRepository metadataFetchJobRepository;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    @Transactional
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            int deleted;
            if (request.isTriggeredByCron()) {
                Instant cutoff = Instant.now().minus(3, ChronoUnit.DAYS);
                deleted = metadataFetchJobRepository.deleteAllByCompletedAtBefore(cutoff);
                log.info("{}: Removed {} metadata fetch jobs older than {}", getTaskType(), deleted, cutoff);
            } else {
                deleted = metadataFetchJobRepository.deleteAllRecords();
                log.info("{}: Removed all {} metadata fetch jobs (on-demand execution)", getTaskType(), deleted);
            }

            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error cleaning up temp metadata", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEANUP_TEMP_METADATA;
    }

    @Override
    public String getMetadata() {
        long count = metadataFetchJobRepository.countAll();
        return "Metadata " + (count != 1 ? "rows" : "row") + " pending cleanup: " + count;
    }
}