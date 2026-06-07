package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class GrimmlinkMetadataPullItem {
    private String id;
    private String type;
    private Long bookId;
    private Long bookFileId;
    private String dedupeKey;
    private String contentHash;
    private Object payload;
    private String payloadJson;
    private Instant clientUpdatedAt;
    private Instant syncedAt;
    private Instant updatedAt;
    private String device;
    private String deviceId;
}
