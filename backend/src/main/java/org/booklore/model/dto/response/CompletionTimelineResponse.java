package org.booklore.model.dto.response;

import org.booklore.model.enums.ReadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionTimelineResponse {
    private Integer year;
    private Integer month;
    private Long totalBooks;
    private Map<ReadStatus, Long> statusBreakdown;
    private Long finishedBooks;
    private Double completionRate;
}

