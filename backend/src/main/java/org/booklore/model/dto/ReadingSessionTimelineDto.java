package org.booklore.model.dto;

import org.booklore.model.enums.BookFileType;

import java.time.LocalDateTime;

public interface ReadingSessionTimelineDto {
    Long getBookId();

    String getBookTitle();

    BookFileType getBookFileType();

    LocalDateTime getStartDate();

    LocalDateTime getEndDate();

    Long getTotalSessions();

    Long getTotalDurationSeconds();
}
