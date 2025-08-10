package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.enums.EventTaskType;
import com.adityachandel.booklore.model.enums.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage {
    private String taskId;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private String message;
    private EventTaskType taskType;
    private TaskStatus status;

    @JsonProperty
    public String getTitle() {
        return taskType != null ? taskType.getTitle() : null;
    }

    @JsonProperty
    public boolean isCancellable() {
        return taskType != null && taskType.isCancellable();
    }
}