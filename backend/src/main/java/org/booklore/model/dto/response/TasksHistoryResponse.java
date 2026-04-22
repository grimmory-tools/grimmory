package org.booklore.model.dto.response;

import org.booklore.task.TaskStatus;
import org.booklore.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TasksHistoryResponse {
    private List<TaskHistory> taskHistories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskHistory {
        private String id;
        private TaskType type;
        private TaskStatus status;
        private Integer progressPercentage;
        private String message;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;
    }
}
