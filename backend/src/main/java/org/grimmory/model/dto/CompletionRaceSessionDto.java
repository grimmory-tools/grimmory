package org.grimmory.model.dto;

import java.time.Instant;

public interface CompletionRaceSessionDto {
    Long getBookId();
    String getBookTitle();
    Instant getSessionDate();
    Float getEndProgress();
}
