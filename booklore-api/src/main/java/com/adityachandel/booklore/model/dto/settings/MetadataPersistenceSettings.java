package com.adityachandel.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataPersistenceSettings {
    private boolean saveToOriginalFile;
    private boolean convertCbrCb7ToCbz;
    private boolean backupMetadata;
    private boolean backupCover;
}
