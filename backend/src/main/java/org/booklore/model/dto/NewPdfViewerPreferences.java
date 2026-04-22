package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewPdfViewerPreferences {
    private Long bookId;
    private NewPdfPageSpread pageSpread;
    private NewPdfPageViewMode pageViewMode;
    private NewPdfBackgroundColor backgroundColor;
    private NewPdfPageFitMode fitMode;
    private NewPdfPageScrollMode scrollMode;
}