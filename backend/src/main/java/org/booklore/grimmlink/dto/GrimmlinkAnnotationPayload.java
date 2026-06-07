package org.booklore.grimmlink.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class GrimmlinkAnnotationPayload {
    private String dedupeKey;
    private String type;
    private String text;
    private String note;
    private String color;
    private String drawer;
    private String style;
    private String chapter;
    private Integer page;
    private GrimmlinkLocationPayload location;
    private Instant createdAt;
    private Instant updatedAt;
}
