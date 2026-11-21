package org.booklore.model.dto.request;

import org.booklore.model.enums.MergeMetadataType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class DeleteMetadataRequest {
    @NotNull
    private MergeMetadataType metadataType;

    private List<String> valuesToDelete;
    private boolean selectAll;
    private String search;
    private Set<String> excludedValues;

    @AssertTrue(message = "Either valuesToDelete or selectAll must be provided")
    public boolean isSelectionValid() {
        return selectAll || (valuesToDelete != null && !valuesToDelete.isEmpty());
    }
}

