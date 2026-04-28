package org.booklore.model.dto.acquisition;

import java.time.Instant;

public record NzbResult(
        String title,
        String downloadUrl,
        long sizeBytes,
        Instant publishedAt,
        int grabs,
        String indexerName
) {}
