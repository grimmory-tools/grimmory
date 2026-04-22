package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpubViewerPreferences {
    private Long bookId;
    private String theme;
    private String font;
    private String flow;
    private String spread;
    private Integer fontSize;
    private Float letterSpacing;
    private Float lineHeight;
    private Long customFontId;
}