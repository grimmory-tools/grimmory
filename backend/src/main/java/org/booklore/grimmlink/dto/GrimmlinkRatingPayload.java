package org.booklore.grimmlink.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class GrimmlinkRatingPayload {
    private String dedupeKey;
    private Integer value;
    private Integer scale;
    private String source;
    private Instant updatedAt;
}
