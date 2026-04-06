package org.booklore.model.dto;

import java.time.LocalDate;

public interface SessionScatterDto {
    Double getHourOfDay();
    Double getDurationMinutes();
    LocalDate getSessionDate();
}
