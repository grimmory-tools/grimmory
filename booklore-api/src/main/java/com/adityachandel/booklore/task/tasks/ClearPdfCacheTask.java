package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.model.enums.UserPermission;
import com.adityachandel.booklore.task.TaskMetadataHelper;
import com.adityachandel.booklore.task.TaskStatus;
import com.adityachandel.booklore.util.FileService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
@Slf4j
public class ClearPdfCacheTask implements Task {

    private FileService fileService;

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
                .taskType(TaskType.CLEAR_PDF_CACHE);

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        try {
            fileService.clearCacheDirectory(fileService.getPdfCachePath());
            log.info("{}: Cache cleared", getTaskType());
            builder.status(TaskStatus.COMPLETED);
        } catch (Exception e) {
            log.error("{}: Error clearing cache", getTaskType(), e);
            builder.status(TaskStatus.FAILED);
            throw new RuntimeException("Failed to clear PDF cache", e);
        }

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        return builder.build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.CLEAR_PDF_CACHE;
    }

    @Override
    public String getMetadata() {
        return TaskMetadataHelper.getCacheSizeString(fileService.getPdfCachePath());
    }
}
