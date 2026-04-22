package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMoveResult {
    private boolean moved;
    private String newFileName;
    private String newFileSubPath;
}
