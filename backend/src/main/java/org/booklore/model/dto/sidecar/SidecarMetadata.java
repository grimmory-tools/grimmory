package org.booklore.model.dto.sidecar;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SidecarMetadata {
    @Builder.Default
    private String version = "1.0";
    private Instant generatedAt;
    @Builder.Default
    private String generatedBy = "booklore";
    private SidecarBookMetadata metadata;
    private SidecarCoverInfo cover;
}
