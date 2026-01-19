package com.adityachandel.booklore.config;

import com.adityachandel.booklore.context.KomgaCleanContext;
import com.adityachandel.booklore.model.dto.komga.KomgaBookMetadataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KomgaCleanFilterTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        // Create ObjectMapper with our custom configuration
        JacksonConfig config = new JacksonConfig();
        objectMapper = config.komgaCleanObjectMapper(new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder());
    }

    @AfterEach
    void cleanup() {
        KomgaCleanContext.clear();
    }

    @Test
    void shouldExcludeLockFieldsInCleanMode() throws Exception {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);

        KomgaBookMetadataDto metadata = KomgaBookMetadataDto.builder()
                .title("Test Book")
                .titleLock(true)
                .summary("Test Summary")
                .summaryLock(false)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Lock fields should be excluded
        assertThat(result).containsKey("title");
        assertThat(result).containsKey("summary");
        assertThat(result).doesNotContainKey("titleLock");
        assertThat(result).doesNotContainKey("summaryLock");
    }

    @Test
    void shouldExcludeNullValuesInCleanMode() throws Exception {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);

        KomgaBookMetadataDto metadata = KomgaBookMetadataDto.builder()
                .title("Test Book")
                .summary(null)  // Null value
                .number("1")
                .releaseDate(null)  // Null value
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Null values should be excluded
        assertThat(result).containsKey("title");
        assertThat(result).containsKey("number");
        assertThat(result).doesNotContainKey("summary");
        assertThat(result).doesNotContainKey("releaseDate");
    }

    @Test
    void shouldExcludeEmptyArraysInCleanMode() throws Exception {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);

        KomgaBookMetadataDto metadata = KomgaBookMetadataDto.builder()
                .title("Test Book")
                .authors(new ArrayList<>())  // Empty list
                .tags(new ArrayList<>())     // Empty list
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Empty arrays should be excluded
        assertThat(result).containsKey("title");
        assertThat(result).doesNotContainKey("authors");
        assertThat(result).doesNotContainKey("tags");
    }

    @Test
    void shouldIncludeNonEmptyArraysInCleanMode() throws Exception {
        // Given: Clean mode is enabled
        KomgaCleanContext.setCleanMode(true);

        List<String> tags = new ArrayList<>();
        tags.add("fiction");
        tags.add("adventure");

        KomgaBookMetadataDto metadata = KomgaBookMetadataDto.builder()
                .title("Test Book")
                .tags(tags)  // Non-empty list
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Non-empty arrays should be included
        assertThat(result).containsKey("title");
        assertThat(result).containsKey("tags");
        assertThat((List<?>) result.get("tags")).hasSize(2);
    }

    @Test
    void shouldIncludeAllFieldsWhenCleanModeDisabled() throws Exception {
        // Given: Clean mode is disabled (default)
        KomgaCleanContext.setCleanMode(false);

        KomgaBookMetadataDto metadata = KomgaBookMetadataDto.builder()
                .title("Test Book")
                .titleLock(true)
                .summary(null)  // Null value
                .summaryLock(false)
                .authors(new ArrayList<>())  // Empty list
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Lock fields and empty arrays should be included
        assertThat(result).containsKey("title");
        assertThat(result).containsKey("titleLock");
        assertThat(result).containsKey("summaryLock");
        assertThat(result).containsKey("authors");
        // Note: null values are excluded by @JsonInclude(JsonInclude.Include.NON_NULL) regardless
    }
}
