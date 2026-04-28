package org.booklore.model.dto.acquisition;

public record IndexerDTO(
        Long id,
        String name,
        String url,
        String apiKey,
        boolean enabled,
        int priority
) {
}
