package com.adityachandel.booklore.model.dto.settings;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CoverCroppingSettings {
    private boolean verticalCroppingEnabled;
    private boolean horizontalCroppingEnabled;
    private double aspectRatioThreshold;
    private boolean smartCroppingEnabled;
}
