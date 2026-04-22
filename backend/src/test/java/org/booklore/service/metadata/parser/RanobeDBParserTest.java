package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RanobeDbParserTest {

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private RanobeDbParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFetchMetadata_EmptyQuery() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .build();
        // Empty query - no title

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when query is empty");
    }

    @Test
    @Disabled("Integration test - requires network access to RanobeDB.org")
    void testFetchMetadata_Integration_RealBook() {
        // Given
        Book book = Book.builder()
            .title("That Time I Got Reincarnated as a Slime, Vol. 1")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("That Time I Got Reincarnated as a Slime, Vol. 1")
            .author("Fuse")
            .build();

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should return results for real book");

        BookMetadata firstResult = results.getFirst();
        assertNotNull(firstResult.getTitle(), "Title should be present");
        assertNotNull(firstResult.getRanobedbId(), "RanobeDB ID should be present");
        assertTrue(firstResult.getAuthors() != null && !firstResult.getAuthors().isEmpty(),
            "Authors should be present");
    }
}
