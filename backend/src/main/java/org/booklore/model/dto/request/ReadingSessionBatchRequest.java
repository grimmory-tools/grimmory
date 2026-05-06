package org.booklore.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.BookFileType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionBatchRequest {
    @NotNull
    private Long bookId;

    private BookFileType bookType;

    @Valid
    @NotEmpty
    private List<ReadingSessionItemRequest> sessions;
}
