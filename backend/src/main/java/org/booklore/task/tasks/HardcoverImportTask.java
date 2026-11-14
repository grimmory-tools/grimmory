package org.booklore.task.tasks;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.entity.TaskHistoryEntity;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.repository.TaskHistoryRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.hardcover.HardcoverSyncSettingsService;
import org.booklore.task.TaskStatus;
import org.booklore.util.TaskUtils;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@RequiredArgsConstructor 
@Slf4j
public class HardcoverImportTask implements Task {
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;
    private final HardcoverSyncService hardcoverSyncService;
    private final TaskUtils taskUtils;
    private final TaskHistoryRepository taskHistoryRepository;
    public static final long MIN_NOTIFICATION_INTERVAL_MS = 250;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!hardcoverSyncSettingsService.getSettingsForUserId(user.getId()).isHardcoverSyncEnabledForUser()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Hardcover sync is disabled.");
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        log.info("{}: Task started", getTaskType());

        TaskHistoryEntity task = taskHistoryRepository.findById(request.getTaskId()).orElseThrow();

        hardcoverSyncService.importHardcoverData(task.getUserId(), Boolean.parseBoolean(String.valueOf(task.getTaskOptions().get("overwrite"))));

        taskUtils.sendTaskProgressNotification(request.getTaskId(), TaskType.HARDCOVER_IMPORT, 0, request.getTaskId(), TaskStatus.IN_PROGRESS, 0, false, MIN_NOTIFICATION_INTERVAL_MS);

        return TaskCreateResponse
                .builder()
                .taskType(TaskType.HARDCOVER_IMPORT)
                .taskId(request.getTaskId())
                .status(TaskStatus.IN_PROGRESS)
                .build();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.HARDCOVER_IMPORT;
    }
}
