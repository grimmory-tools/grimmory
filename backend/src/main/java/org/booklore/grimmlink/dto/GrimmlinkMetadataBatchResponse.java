package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkMetadataBatchResponse {
    private boolean ok;
    private GrimmlinkMetadataSyncResponse push;
    private GrimmlinkMetadataPullResponse pull;
}
