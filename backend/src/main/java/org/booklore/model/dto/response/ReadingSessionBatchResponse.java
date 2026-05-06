package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionBatchResponse {
    private Integer totalRequested;
    private Integer successCount;
    private List<SessionResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResult {
        private Long sessionId;
        private Instant startTime;
        private Instant endTime;
    }
}
