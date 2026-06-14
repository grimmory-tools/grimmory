package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.booklore.model.enums.BookFileType;

import java.util.Set;

@Data
public class BookConversionRequest {
    @NotEmpty(message = "At least one book ID is required")
    @Size(max = 500)
    private Set<Long> bookIds;

    @NotNull
    private BookFileType targetFormat;
}
