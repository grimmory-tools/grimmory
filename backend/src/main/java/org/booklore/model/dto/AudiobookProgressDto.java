package org.booklore.model.dto;

public interface AudiobookProgressDto {
    Long getBookId();
    String getTitle();
    Float getMaxProgress();
    Long getTotalDurationSeconds();
    Long getListenedDurationSeconds();
}
