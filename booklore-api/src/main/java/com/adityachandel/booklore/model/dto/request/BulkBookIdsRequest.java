package com.adityachandel.booklore.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class BulkBookIdsRequest {
    @NotEmpty(message = "At least one book ID is required")
    private Set<Long> bookIds;
}
