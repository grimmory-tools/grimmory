package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkItemResult {
    private String type;
    private String dedupeKey;
    private String status;
    private String id;
    private String reason;
}
