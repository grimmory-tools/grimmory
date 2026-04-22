package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.BookMetadata;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookdropPatternExtractResult {
    private int totalFiles;
    private int successfullyExtracted;
    private int failed;
    private List<FileExtractionResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileExtractionResult {
        private Long fileId;
        private String fileName;
        private boolean success;
        private BookMetadata extractedMetadata;
        private String errorMessage;
    }
}
