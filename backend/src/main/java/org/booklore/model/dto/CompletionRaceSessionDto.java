package org.booklore.model.dto;

import java.time.Instant;

public interface CompletionRaceSessionDto {
    Long getBookId();
    String getBookTitle();
    Instant getSessionDate();
    Float getEndProgress();
}
