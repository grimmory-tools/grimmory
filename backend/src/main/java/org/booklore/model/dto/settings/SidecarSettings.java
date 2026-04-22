package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SidecarSettings {
    private boolean enabled;
    private boolean writeOnUpdate;
    private boolean writeOnScan;
    private boolean includeCoverFile;
}
