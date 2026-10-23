package org.booklore.util;

import org.booklore.model.enums.TaskType;
import org.booklore.model.websocket.TaskProgressPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.task.TaskStatus;
import org.booklore.task.tasks.HardcoverImportTask;
import org.springframework.scheduling.config.Task;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@RequiredArgsConstructor 
@Slf4j
public class TaskUtils {
    private final NotificationService notificationService;

    public long sendTaskProgressNotification(String taskId, TaskType taskType, int progress, String message, TaskStatus taskStatus, long lastNotificationTime, boolean force, long minNotificationIntervalMs) {
        long currentTime = System.currentTimeMillis();
    
        // Send if forced (start/end) or if enough time has passed
        if (force || (currentTime - lastNotificationTime) >= minNotificationIntervalMs) {
            try {
                TaskProgressPayload payload = TaskProgressPayload.builder()
                        .taskId(taskId)
                        .taskType(taskType)
                        .message(message)
                        .progress(progress)
                        .taskStatus(taskStatus)
                        .build();
    
                notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
                return currentTime;
            } catch (Exception e) {
                log.error("Failed to send task progress notification for taskId={}: {}", taskId, e.getMessage(), e);
            }
        }
    
        return lastNotificationTime;
    }
}
