package org.grimmory.model.dto;

import java.time.Instant;

public interface BookTimelineDto {
    Long getBookId();
    String getTitle();
    Integer getPageCount();
    Instant getFirstSessionStart();
    Instant getLastSessionEnd();
    Integer getTotalSessions();
    Long getTotalDurationSeconds();
    Double getMaxProgress();
    String getReadStatus();
}
