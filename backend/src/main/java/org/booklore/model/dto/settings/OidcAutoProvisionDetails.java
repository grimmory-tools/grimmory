package org.booklore.model.dto.settings;

import lombok.Data;

import java.util.List;

@Data
public class OidcAutoProvisionDetails {
    private boolean enableAutoProvisioning;
    private boolean allowLocalAccountLinking = true;
    private List<String> defaultPermissions;
    private List<Long> defaultLibraryIds;
}
