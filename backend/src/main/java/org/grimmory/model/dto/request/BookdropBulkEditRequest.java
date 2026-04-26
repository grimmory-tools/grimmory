package org.grimmory.model.dto.request;

import org.grimmory.model.dto.BookMetadata;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

@Data
public class BookdropBulkEditRequest {
    @NotNull
    private BookMetadata fields;
    @NotNull
    private Set<String> enabledFields;
    private boolean mergeArrays;
    private boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
}
