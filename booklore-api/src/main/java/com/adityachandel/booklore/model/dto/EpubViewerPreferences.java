package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EpubViewerPreferences {
    private Long bookId;
    private String theme;
    private String font;
    private String flow;
    private Integer fontSize;
    private Float letterSpacing;
    private Float lineHeight;
}