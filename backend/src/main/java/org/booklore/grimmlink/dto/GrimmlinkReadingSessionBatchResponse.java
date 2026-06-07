package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GrimmlinkReadingSessionBatchResponse {
    private int totalRequested;
    private int successCount;
    private List<SessionResult> results;

    @Data
    @Builder
    public static class SessionResult {
        private Long sessionId;
        private Instant startTime;
        private Instant endTime;
    }
}
