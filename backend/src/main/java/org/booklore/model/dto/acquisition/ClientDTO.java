package org.booklore.model.dto.acquisition;

public record ClientDTO(
        Long id,
        String name,
        String type,
        String url,
        String apiKey,
        String category,
        boolean enabled
) {
}
