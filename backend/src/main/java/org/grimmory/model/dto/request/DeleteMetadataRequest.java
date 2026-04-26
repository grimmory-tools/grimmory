package org.grimmory.model.dto.request;

import org.grimmory.model.enums.MergeMetadataType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DeleteMetadataRequest {
    @NotNull
    private MergeMetadataType metadataType;

    @NotEmpty
    private List<String> valuesToDelete;
}

