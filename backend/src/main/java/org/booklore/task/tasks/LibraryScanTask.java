package org.booklore.task.tasks;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.service.library.LibraryService;
import org.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LibraryScanTask implements Task {

    private final LibraryService libraryService;

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
            for (Library library : libraryService.getAllLibraries()) {
                try {
                    libraryService.rescanLibrary(library.getId());
                    log.info("{}: Rescanned library '{}'", getTaskType(), library.getName());
                } catch (Exception e) {
                    log.error("{}: Failed to rescan library '{}': {}", getTaskType(), library.getName(), e.getMessage(), e);
                }
            }

            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error scanning libraries", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.SYNC_LIBRARY_FILES;
    }
}