package org.grimmory.task;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TaskCancellationManager {

    private final Cache<String, Boolean> cancelledTasks = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofHours(24))
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

