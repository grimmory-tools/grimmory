package com.adityachandel.booklore.config;

import com.adityachandel.booklore.context.KomgaCleanContext;
import com.adityachandel.booklore.model.dto.komga.KomgaSeriesMetadataDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Demonstrates the JSON output differences with and without clean mode.
 */
class KomgaCleanModeDemo {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        JacksonConfig config = new JacksonConfig();
        objectMapper = config.komgaCleanObjectMapper(new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder());
    }

    @AfterEach
    void cleanup() {
        KomgaCleanContext.clear();
    }

    @Test
    void demonstrateCleanModeEffect() throws Exception {
        // Create a sample series metadata
        KomgaSeriesMetadataDto metadata = KomgaSeriesMetadataDto.builder()
                .status("ONGOING")
                .statusLock(false)
                .title("My Awesome Series")
                .titleLock(false)
                .titleSort("My Awesome Series")
                .titleSortLock(false)
                .summary(null)  // No summary available
                .summaryLock(false)
                .readingDirection("LEFT_TO_RIGHT")
                .readingDirectionLock(false)
                .publisher(null)  // No publisher available
                .publisherLock(false)
                .language(null)  // No language specified
                .languageLock(false)
                .genres(new ArrayList<>())
                .genresLock(false)
                .tags(new ArrayList<>())
                .tagsLock(false)
                .totalBookCount(10)
                .totalBookCountLock(false)
                .ageRatingLock(false)
                .build();

        // Test without clean mode
        KomgaCleanContext.setCleanMode(false);
        String normalJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        System.out.println("=== WITHOUT CLEAN MODE ===");
        System.out.println(normalJson);
        System.out.println("JSON size: " + normalJson.length() + " bytes\n");

        // Test with clean mode
        KomgaCleanContext.setCleanMode(true);
        String cleanJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
        System.out.println("=== WITH CLEAN MODE (clean=true) ===");
        System.out.println(cleanJson);
        System.out.println("JSON size: " + cleanJson.length() + " bytes\n");

        // Verify size difference
        System.out.println("Size reduction: " + (normalJson.length() - cleanJson.length()) + " bytes");
        System.out.println("Percentage smaller: " + String.format("%.1f", 100.0 * (normalJson.length() - cleanJson.length()) / normalJson.length()) + "%");

        // Assert that clean mode produces smaller output
        assertThat(cleanJson.length()).isLessThan(normalJson.length());
        
        // Assert that clean JSON doesn't contain "Lock" fields
        assertThat(cleanJson).doesNotContain("Lock");
    }
}
