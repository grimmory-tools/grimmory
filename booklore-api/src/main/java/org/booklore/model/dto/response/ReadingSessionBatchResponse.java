package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingSessionBatchResponse {
    private int totalRequested;
    private int successCount;
    private List<SessionResult> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionResult {
        private Long sessionId;
        private Instant startTime;
        private Instant endTime;
    }
}
