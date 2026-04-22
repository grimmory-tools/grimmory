package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionScatterResponse {
    private Double hourOfDay;
    private Double durationMinutes;
    private Integer dayOfWeek;
}
