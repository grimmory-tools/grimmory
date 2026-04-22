package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LongestAudiobookResponse {
    private Long bookId;
    private String title;
    private Long totalDurationSeconds;
    private Long listenedDurationSeconds;
    private Double progressPercent;
}
