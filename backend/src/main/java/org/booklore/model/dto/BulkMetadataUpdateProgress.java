package org.booklore.model.dto;

import java.util.List;

public record BulkMetadataUpdateProgress(
        int processedCount,
        int totalCount,
        int succeededCount,
        int failedCount,
        List<BulkMetadataUpdateFailure> failedBooks
) {
}
