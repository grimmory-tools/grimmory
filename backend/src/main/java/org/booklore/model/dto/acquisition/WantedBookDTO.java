package org.booklore.model.dto.acquisition;

import org.booklore.model.enums.WantedBookStatus;

import java.time.Instant;

public record WantedBookDTO(
        Long id,
        String title,
        String author,
        String isbn13,
        String isbn10,
        String provider,
        String providerBookId,
        String thumbnailUrl,
        WantedBookStatus status,
        Instant lastCheckedAt,
        String downloadId,
        Long addedById,
        Instant addedAt
) {
}
