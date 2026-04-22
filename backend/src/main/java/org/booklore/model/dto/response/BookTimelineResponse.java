package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookTimelineResponse {
    private Long bookId;
    private String title;
    private Integer pageCount;
    private LocalDate firstSessionDate;
    private LocalDate lastSessionDate;
    private Integer totalSessions;
    private Long totalDurationSeconds;
    private Double maxProgress;
    private String readStatus;
}
