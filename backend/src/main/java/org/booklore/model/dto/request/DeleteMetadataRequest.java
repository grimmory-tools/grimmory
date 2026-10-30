package org.booklore.model.dto.request;

import org.booklore.model.enums.MergeMetadataType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeleteMetadataRequest {
    @NotNull
    private MergeMetadataType metadataType;

    @NotNull
    @Valid
    private MetadataSelection selection;
}

