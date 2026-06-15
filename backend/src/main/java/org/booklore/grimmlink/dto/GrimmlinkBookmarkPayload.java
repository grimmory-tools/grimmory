package org.booklore.grimmlink.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class GrimmlinkBookmarkPayload {
    private String dedupeKey;
    private String title;
    private String notes;
    private String chapter;
    private Integer page;
    private GrimmlinkLocationPayload location;
    private Instant createdAt;
    private Instant updatedAt;
    private Boolean deleted;
}
