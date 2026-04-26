package org.grimmory.model.dto;

import java.time.Instant;

public interface PageTurnerSessionDto {
    Long getBookId();
    String getBookTitle();
    Integer getPageCount();
    Integer getPersonalRating();
    Instant getDateFinished();
    Instant getStartTime();
    Instant getEndTime();
    Integer getDurationSeconds();
}
