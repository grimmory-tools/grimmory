package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record BookFileProgress(
        @NotNull Long bookFileId,
        String positionData,
        String positionHref,
        @NotNull Float progressPercent,
        String ttsPositionCfi) {
}
