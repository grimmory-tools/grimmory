package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.TaskType;
import org.booklore.task.TaskStatus;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateResponse {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
}
