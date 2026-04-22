package org.booklore.model.dto;

public interface ListeningAuthorDto {
    String getAuthorName();
    Long getBookCount();
    Long getTotalSessions();
    Long getTotalDurationSeconds();
}
