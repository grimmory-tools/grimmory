package org.booklore.model.dto.response.kobo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KoboReadingStateResponse {
    private String requestResult;
    private List<UpdateResult> updateResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateResult {
        private String entitlementId;
        private Result currentBookmarkResult;
        private Result statisticsResult;
        private Result statusInfoResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String result;

        public static Result success() {
            return Result.builder().result("Success").build();
        }
    }
}
