package org.booklore.model.dto;

public record BulkMetadataUpdateFailure(Long bookId, String title, String reason) {
}
