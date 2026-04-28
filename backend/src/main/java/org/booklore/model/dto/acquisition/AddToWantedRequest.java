package org.booklore.model.dto.acquisition;

public record AddToWantedRequest(
        String title,
        String author,
        String isbn13,
        String isbn10,
        String provider,
        String providerBookId,
        String thumbnailUrl
) {
}
