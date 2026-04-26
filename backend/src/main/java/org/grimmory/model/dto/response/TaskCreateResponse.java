package org.grimmory.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.grimmory.model.enums.TaskType;
import org.grimmory.task.TaskStatus;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateResponse {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
}
