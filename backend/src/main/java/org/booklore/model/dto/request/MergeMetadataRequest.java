package org.booklore.model.dto.request;

import org.booklore.model.enums.MergeMetadataType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class MergeMetadataRequest {
    @NotNull
    private MergeMetadataType metadataType;

    @NotEmpty
    private List<@NotBlank String> targetValues;

    private List<String> valuesToMerge;
    private boolean selectAll;
    private String search;
    private Set<String> excludedValues;

    @AssertTrue(message = "Either valuesToMerge or selectAll must be provided")
    public boolean isSelectionValid() {
        return selectAll || (valuesToMerge != null && !valuesToMerge.isEmpty());
    }
}
