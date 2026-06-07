package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GrimmlinkMetadataPullResponse {
    private Long bookId;
    private Long bookFileId;
    private boolean ok;
    private Instant since;
    private Instant nextCursor;
    private Integer limit;
    private List<GrimmlinkMetadataPullItem> items;
}
