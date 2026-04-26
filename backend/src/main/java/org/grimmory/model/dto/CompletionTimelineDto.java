package org.grimmory.model.dto;

import org.grimmory.model.enums.ReadStatus;

public interface CompletionTimelineDto {
    Integer getYear();
    Integer getMonth();
    ReadStatus getReadStatus();
    Long getBookCount();
}

