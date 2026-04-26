package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record DuplicateDetectionRequest(
        @NotNull Long libraryId,
        boolean matchByIsbn,
        boolean matchByExternalId,
        boolean matchByTitleAuthor,
        boolean matchByDirectory,
        boolean matchByFilename
) {}
