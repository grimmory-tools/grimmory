package org.booklore.model.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BookdropSelectionRequest {
    private boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
}
