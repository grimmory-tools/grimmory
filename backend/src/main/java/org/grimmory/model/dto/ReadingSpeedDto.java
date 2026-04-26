package org.grimmory.model.dto;

import java.time.LocalDate;


public interface ReadingSpeedDto {
    Integer getTotalSessions();
    Double getAvgProgressPerMinute();
    LocalDate getDate();
}

