package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyPaceResponse {
    private Integer year;
    private Integer month;
    private Long booksCompleted;
    private Long totalListeningSeconds;
}
