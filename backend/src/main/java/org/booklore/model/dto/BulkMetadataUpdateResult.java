package org.booklore.model.dto;

public record BulkMetadataUpdateResult(
        int processedCount,
        int totalCount,
        int succeededCount,
        int failedCount
) {
}
