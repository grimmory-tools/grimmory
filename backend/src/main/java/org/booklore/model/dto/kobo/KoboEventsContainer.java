package org.booklore.model.dto.kobo;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class KoboEventsContainer {
    private String affiliateName;
    private String applicationVersion;
    private String platformId;
    private String serialNumber;
    private List<Event> events;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class Event {
        private String clientApplicationVersion;
        private String eventType;
        private String id;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssX")
        private Instant timestamp;
        private Attributes attributes;
        private Metrics metrics;

        private Map<String, Object> unknownProperties;
        @JsonAnySetter
        public void addUnknown(String name, Object value) {
            if (unknownProperties == null) {
                unknownProperties = new HashMap<>();
            }
            unknownProperties.put(name, value);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class Attributes {
        private String contentFormat;
        private String contentType;
        private String monetization;
        private String origin;
        private String startFile;
        private String startSpan;
        private String viewType;
        @JsonProperty("Title")
        @JsonAlias("title")
        private String title;
        private String author;
        @JsonProperty("progress")
        private Float progress;
        @JsonProperty("volumeid")
        private String volumeid;

        private Map<String, Object> unknownProperties;
        @JsonAnySetter
        public void addUnknown(String name, Object value) {
            if (unknownProperties == null) {
                unknownProperties = new HashMap<>();
            }
            unknownProperties.put(name, value);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class Metrics {
        private Integer idleTime;
        private Integer pagesTurned;
        private Integer secondsRead;
        @JsonAlias("stars")
        private Integer stars;

        private Map<String, Object> unknownProperties;
        @JsonAnySetter
        public void addUnknown(String name, Object value) {
            if (unknownProperties == null) {
                unknownProperties = new HashMap<>();
            }
            unknownProperties.put(name, value);
        }
    }
}