package org.booklore.model.dto.kobo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Payload a Kobo device posts to {@code /v1/analytics/event}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class Event {
        private String clientApplicationVersion;
        private String eventType;
        private String id;
        @JsonDeserialize(using = LenientInstantDeserializer.class)
        private Instant timestamp;
        private Attributes attributes;
        private Metrics metrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class Metrics {
        private Integer idleTime;
        private Integer pagesTurned;
        private Integer secondsRead;
        @JsonAlias("stars")
        private Integer stars;
    }

    /**
     * Parses an event timestamp without failing the whole batch: a timestamp that cannot be parsed becomes
     * {@code null}, and events without a timestamp are skipped when sessions are recorded.
     */
    @Slf4j
    public static class LenientInstantDeserializer extends StdDeserializer<Instant> {
        private static final DateTimeFormatter KOBO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]X");

        public LenientInstantDeserializer() {
            super(Instant.class);
        }

        /**
         * Reads the timestamp as ISO-8601 instant or offset date-time.
         *
         * @return the parsed instant, or {@code null} if the value is not a parseable timestamp
         */
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            JsonNode node = p.readValueAsTree();
            if (node == null || !node.isString()) {
                log.debug("Ignoring non-string Kobo event timestamp: {}", node);
                return null;
            }
            String raw = node.stringValue().trim();
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException ignored) {
                // fall through to the offset formats Kobo has been seen to send
            }
            try {
                return OffsetDateTime.parse(raw, KOBO_TIMESTAMP).toInstant();
            } catch (DateTimeParseException e) {
                log.debug("Ignoring unparseable Kobo event timestamp \"{}\"", raw);
                return null;
            }
        }
    }
}
