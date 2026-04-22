package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningAuthorResponse {
    private String author;
    private Long bookCount;
    private Long totalSessions;
    private Long totalDurationSeconds;
}
