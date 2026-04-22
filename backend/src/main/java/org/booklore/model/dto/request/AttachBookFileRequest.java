package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AttachBookFileRequest {
    @NotNull
    @NotEmpty
    private List<Long> sourceBookIds;
    private boolean moveFiles;
}
