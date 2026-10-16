package org.booklore.model.dto;

import lombok.Data;

@Data
public class HardcoverSyncSettings {
    private String hardcoverApiKey;
    private boolean hardcoverSyncEnabled;

    /**
     * Check if Hardcover sync is enabled for a specific user.
     */
    public boolean isHardcoverSyncEnabledForUser() {    
        return isHardcoverSyncEnabled() 
                && getHardcoverApiKey() != null 
                && !getHardcoverApiKey().isBlank();
    }
}
