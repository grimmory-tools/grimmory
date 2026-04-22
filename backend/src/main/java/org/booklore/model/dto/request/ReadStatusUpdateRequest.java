package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReadStatusUpdateRequest {
    @NotEmpty(message = "Book IDs cannot be empty")
    private List<Long> bookIds;

    @NotNull(message = "Status cannot be null")
    private String status;
}
