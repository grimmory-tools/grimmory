package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreStatisticsResponse {
    private String genre;
    private Long bookCount;
    private Long totalSessions;
    private Long totalDurationSeconds;
    private Double averageSessionsPerBook;
}

