package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyListeningTrendResponse {
    private Integer year;
    private Integer week;
    private Long totalDurationSeconds;
    private Long sessions;
}
