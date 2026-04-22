package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.KoboReadStatus;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoboReadingState {
    private String entitlementId;
    private String created;
    private String lastModified;
    private StatusInfo statusInfo;
    private Statistics statistics;
    private CurrentBookmark currentBookmark;
    private String priorityTimestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StatusInfo {
        private String lastModified;
        private KoboReadStatus status;
        private Integer timesStartedReading;
        private String lastTimeStartedReading;
        private String lastTimeFinished;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Statistics {
        private String lastModified;
        private Integer spentReadingMinutes;
        private Integer remainingTimeMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CurrentBookmark {
        private String lastModified;
        private Integer progressPercent;
        private Integer contentSourceProgressPercent;
        private Location location;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Location {
            private String value;
            private String type;
            private String source;
        }
    }
}
