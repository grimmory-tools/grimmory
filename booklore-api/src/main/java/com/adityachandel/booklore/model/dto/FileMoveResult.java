package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FileMoveResult {
    private boolean moved;
    private String newFileName;
    private String newFileSubPath;
}
