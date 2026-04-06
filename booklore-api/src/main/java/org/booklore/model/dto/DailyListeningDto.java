package org.booklore.model.dto;

import java.time.LocalDate;

public interface DailyListeningDto {
    LocalDate getSessionDate();
    Long getTotalDurationSeconds();
    Long getSessions();
}
