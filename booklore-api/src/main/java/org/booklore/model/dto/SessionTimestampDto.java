package org.booklore.model.dto;

import java.time.Instant;

public interface SessionTimestampDto {
    Instant getStartTime();
    Integer getDurationSeconds();
}
