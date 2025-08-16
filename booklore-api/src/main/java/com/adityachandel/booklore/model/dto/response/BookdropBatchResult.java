package com.adityachandel.booklore.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class BookdropBatchResult {
    private int totalFiles;
    private int successfullyImported;
    private int failed;
    private Instant processedAt;
    private List<BookdropFileResult> fileResults;
}
