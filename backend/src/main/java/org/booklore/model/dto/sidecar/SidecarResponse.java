package org.booklore.model.dto.sidecar;

import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

@Getter
@Builder
public class SidecarResponse {
    private final long lastModified;
    private final Optional<SidecarMetadata> metadata;
}
