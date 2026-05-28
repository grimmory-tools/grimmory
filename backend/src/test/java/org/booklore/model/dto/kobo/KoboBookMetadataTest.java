package org.booklore.model.dto.kobo;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KoboBookMetadataTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldIncludeSubtitleWhenNotNull() {
        // Given: Book has a subtitle
        KoboBookMetadata metadata = KoboBookMetadata.builder()
                .title("Test Book")
                .subtitle("A Subtitle")
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Subtitle should be included
        assertThat(result)
                .containsKey("Title")
                .containsKey("Subtitle");
    }

    @Test
    void shouldExcludeSubtitleWhenNull() {
        // Given: Book does not have a subtitle
        KoboBookMetadata metadata = KoboBookMetadata.builder()
                .title("Test Book")
                .subtitle(null)
                .build();

        // When: Serializing to JSON
        String json = objectMapper.writeValueAsString(metadata);
        Map<String, Object> result = objectMapper.readValue(json, Map.class);

        // Then: Subtitle should be excluded
        assertThat(result)
                .containsKey("Title")
                .doesNotContainKey("Subtitle");
    }

}
