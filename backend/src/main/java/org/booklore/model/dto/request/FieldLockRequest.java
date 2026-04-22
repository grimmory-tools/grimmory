package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FieldLockRequest {

    @NotNull(message = "Book ID must not be null")
    private Long bookId;

    @NotNull(message = "Field must not be null")
    private String field;

    private Boolean isLocked;
}
