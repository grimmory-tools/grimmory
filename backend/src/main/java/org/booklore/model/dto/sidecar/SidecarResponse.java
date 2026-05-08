package org.booklore.model.dto.sidecar;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SidecarResponse {
    private final Instant lastModified;
    private final SidecarMetadata metadata;
}
