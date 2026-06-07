package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkShelfRemovalResponse {
    private Long shelfId;
    private Long bookId;
    private String shelfType;
    private boolean removed;
    private String status;
    private String message;
}
