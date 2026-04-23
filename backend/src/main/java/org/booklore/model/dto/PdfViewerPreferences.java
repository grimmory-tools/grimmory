package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfViewerPreferences {
    private Long bookId;
    private Long userId;
    private String zoom;
    private String spread;
    private Boolean isDarkTheme;
}