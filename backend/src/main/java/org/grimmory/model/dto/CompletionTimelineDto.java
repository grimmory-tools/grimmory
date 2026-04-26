package org.grimmory.model.dto;

import org.booklore.model.enums.ReadStatus;

public interface CompletionTimelineDto {
    Integer getYear();
    Integer getMonth();
    ReadStatus getReadStatus();
    Long getBookCount();
}

