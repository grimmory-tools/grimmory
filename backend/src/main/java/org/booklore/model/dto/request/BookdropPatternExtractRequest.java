package org.booklore.model.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
public class BookdropPatternExtractRequest {
    @NotBlank
    private String pattern;
    private Boolean selectAll;
    private List<Long> excludedIds;
    private List<Long> selectedIds;
    private Boolean preview;
}
