package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoverCroppingSettings {
    private boolean verticalCroppingEnabled;
    private boolean horizontalCroppingEnabled;
    private double aspectRatioThreshold;
    private boolean smartCroppingEnabled;
}
