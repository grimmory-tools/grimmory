package org.booklore.model.dto;

import org.booklore.model.dto.response.CronConfig;
import org.booklore.model.enums.TaskType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskInfo {
    private TaskType taskType;
    private String name;
    private String description;
    private boolean parallel;
    private boolean async;
    private boolean cronSupported;
    private CronConfig cronConfig;
    private String metadata;

    public static TaskInfo fromTaskType(TaskType taskType) {
        return TaskInfo.builder()
                .taskType(taskType)
                .name(taskType.getName())
                .description(taskType.getDescription())
                .parallel(taskType.isParallel())
                .async(taskType.isAsync())
                .cronSupported(taskType.isCronSupported())
                .build();
    }
}
