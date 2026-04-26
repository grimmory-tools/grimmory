package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookdropBatchResult {
    private int totalFiles;
    private int successfullyImported;
    private int failed;
    private Instant processedAt;
    private List<BookdropFileResult> fileResults;
}
