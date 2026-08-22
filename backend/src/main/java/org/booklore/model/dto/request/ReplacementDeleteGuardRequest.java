package org.booklore.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.booklore.model.enums.ReadStatus;

import java.time.Instant;
import java.util.Set;

public record ReplacementDeleteGuardRequest(
        @Positive long predecessorId,
        @Positive long successorId,
        @NotBlank String isbn,
        @NotNull @Valid ReaderState expectedSuccessorState
) {
    public record ReaderState(
            @NotNull ReadStatus status,
            Instant finishedAt,
            Integer rating,
            Set<Long> shelfIds,
            Float progressPercent,
            String progress,
            String progressHref
    ) {}
}
