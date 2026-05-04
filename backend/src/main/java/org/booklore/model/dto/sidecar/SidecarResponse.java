package org.booklore.model.dto.sidecar;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SidecarResponse {
    private final long lastModified;
    private final SidecarMetadata metadata;
}
