package org.booklore.task.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.service.koreader.AnnotationSidecarImporter;
import org.booklore.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SidecarImportTask implements Task {

    private final AnnotationSidecarImporter importer;

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

        long start = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            AnnotationSidecarImporter.ImportResult result = importer.importAll();
            log.info("{}: Task completed in {} ms — {}", getTaskType(),
                    System.currentTimeMillis() - start, result);
            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Task failed", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
        }

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.IMPORT_SIDECAR_ANNOTATIONS;
    }
}
