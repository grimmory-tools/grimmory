package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbookViewerPreferences {
    private Long bookId;
    private Long userId;
    private String fontFamily;
    private Integer fontSize;
    private Float gap;
    private Boolean hyphenate;
    private Boolean isDark;
    private Boolean justify;
    private Float lineHeight;
    private Integer maxBlockSize;
    private Integer maxColumnCount;
    private Integer maxInlineSize;
    private String theme;
    private String flow;
}

