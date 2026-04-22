package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookdropFileNotification {
    private int pendingCount;
    private int totalCount;
    private String lastUpdatedAt;
}
