package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MetadataBatchProgressNotification {
    private String taskId;
    private int completed;
    private int total;
    private String message;
    private String status;
    private boolean isReview;
}
