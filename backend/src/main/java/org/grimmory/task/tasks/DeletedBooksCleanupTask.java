package org.grimmory.task.tasks;

import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.request.TaskCreateRequest;
import org.grimmory.model.dto.response.TaskCreateResponse;
import org.grimmory.model.enums.TaskType;
import org.grimmory.model.enums.UserPermission;
import org.grimmory.repository.BookRepository;
import org.grimmory.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeletedBooksCleanupTask implements Task {

    private final BookRepository bookRepository;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(UUID.randomUUID().toString())
                .taskType(getTaskType());

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            int deletedCount;
            if (request.isTriggeredByCron()) {
                Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
                deletedCount = bookRepository.deleteSoftDeletedBefore(cutoff);
                log.info("{}: Removed {} deleted books older than {}", getTaskType(), deletedCount, cutoff);
            } else {
                deletedCount = bookRepository.deleteAllSoftDeleted();
                log.info("{}: Removed all {} deleted books (on-demand execution)", getTaskType(), deletedCount);
            }
            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error cleaning up deleted books", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEANUP_DELETED_BOOKS;
    }

    @Override
    public String getMetadata() {
        long deleted = bookRepository.countAllSoftDeleted();
        return "Book" + (deleted != 1 ? "s" : "") + " pending cleanup: " + deleted;
    }
}