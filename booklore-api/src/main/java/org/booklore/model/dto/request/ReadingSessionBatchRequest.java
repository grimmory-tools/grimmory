package org.booklore.model.dto.request;

import org.booklore.model.enums.BookFileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionBatchRequest {
    @NotNull(message = "Book ID is required")
    private Long bookId;

    @NotNull(message = "Book type is required")
    private BookFileType bookType;

    @NotEmpty(message = "Sessions list cannot be empty")
    @Valid
    private List<ReadingSessionItemRequest> sessions;
}
