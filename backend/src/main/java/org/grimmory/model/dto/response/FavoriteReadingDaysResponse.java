package org.grimmory.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteReadingDaysResponse {
    private Integer dayOfWeek; // 1=Sunday, 7=Saturday
    private String dayName;
    private Long sessionCount;
    private Long totalDurationSeconds;
}

