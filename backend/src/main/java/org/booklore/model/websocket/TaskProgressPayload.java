package org.booklore.model.websocket;

import org.booklore.model.dto.BulkMetadataUpdateFailure;
import org.booklore.task.TaskStatus;
import org.booklore.model.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressPayload {
    private String taskId;
    private TaskType taskType;
    private String message;
    private int progress; // 0-100 percentage
    private TaskStatus taskStatus;
    private Integer processedCount;
    private Integer totalCount;
    private Integer succeededCount;
    private Integer failedCount;
    private List<BulkMetadataUpdateFailure> failedBooks;
}

