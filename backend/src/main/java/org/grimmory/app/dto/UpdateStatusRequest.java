package org.booklore.app.dto;

import org.booklore.model.enums.ReadStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {
    @NotNull(message = "Status is required")
    private ReadStatus status;
}
