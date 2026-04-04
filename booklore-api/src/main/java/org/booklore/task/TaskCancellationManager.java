package org.booklore.task;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

@Component
public class TaskCancellationManager {

    private final Cache<String, Boolean> cancelledTasks = Caffeine.newBuilder()
            .build();

    public void cancelTask(String taskId) {
        cancelledTasks.put(taskId, Boolean.TRUE);
    }

    public boolean isTaskCancelled(String taskId) {
        return cancelledTasks.getIfPresent(taskId) != null;
    }

    public void clearCancellation(String taskId) {
        cancelledTasks.invalidate(taskId);
    }
}

