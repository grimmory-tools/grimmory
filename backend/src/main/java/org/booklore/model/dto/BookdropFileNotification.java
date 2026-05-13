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
    private BookdropFile addedFile;

    public BookdropFileNotification(int pendingCount, int totalCount, String lastUpdatedAt) {
        this.pendingCount = pendingCount;
        this.totalCount = totalCount;
        this.lastUpdatedAt = lastUpdatedAt;
    }
}
