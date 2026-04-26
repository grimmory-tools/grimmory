package org.grimmory.model.dto;

import lombok.Data;

@Data
public class HardcoverSyncSettings {
    private String hardcoverApiKey;
    private boolean hardcoverSyncEnabled;
}
