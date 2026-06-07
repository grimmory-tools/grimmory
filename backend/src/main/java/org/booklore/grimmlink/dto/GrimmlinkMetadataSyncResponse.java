package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkMetadataSyncResponse {
    private Long bookId;
    private boolean ok;
    private GrimmlinkMetadataSyncResults results;
}
