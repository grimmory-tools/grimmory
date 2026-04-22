package org.booklore.model.dto;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BookMetadataBuilderTest {

    @Test
    void builder_withoutCategories_buildsWithNullCategories() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .build();

        assertNull(metadata.getCategories());
    }

    @Test
    void builder_withNullCategories_doesNotThrow() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Test Book")
                .categories(null)
                .build();

        assertNull(metadata.getCategories());
    }

    @Test
    void builder_withValidCategories_setsCategories() {
        Set<String> categories = Set.of("Fiction", "Thriller");

        BookMetadata metadata = BookMetadata.builder()
                .categories(categories)
                .build();

        assertEquals(categories, metadata.getCategories());
    }

    @Test
    void builder_withNullAuthors_doesNotThrow() {
        BookMetadata metadata = BookMetadata.builder()
                .authors(null)
                .build();

        assertNull(metadata.getAuthors());
    }

    @Test
    void builder_withNullMoods_doesNotThrow() {
        BookMetadata metadata = BookMetadata.builder()
                .moods(null)
                .build();

        assertNull(metadata.getMoods());
    }

    @Test
    void builder_withNullTags_doesNotThrow() {
        BookMetadata metadata = BookMetadata.builder()
                .tags(null)
                .build();

        assertNull(metadata.getTags());
    }

    @Test
    void builder_allCollectionsNull_buildsSuccessfully() {
        BookMetadata metadata = BookMetadata.builder()
                .title("Title Only Book")
                .authors(null)
                .categories(null)
                .moods(null)
                .tags(null)
                .build();

        assertEquals("Title Only Book", metadata.getTitle());
        assertNull(metadata.getAuthors());
        assertNull(metadata.getCategories());
        assertNull(metadata.getMoods());
        assertNull(metadata.getTags());
    }

    @Test
    void builder_minimalMetadata_simulatesPhysicalBookCreation() {
        BookMetadata metadata = BookMetadata.builder()
                .title("My Physical Book")
                .build();

        assertNotNull(metadata);
        assertEquals("My Physical Book", metadata.getTitle());
        assertNull(metadata.getAuthors());
        assertNull(metadata.getCategories());
        assertNull(metadata.getDescription());
    }
}
