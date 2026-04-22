package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LubimyCzytacParserTest {

    @Mock
    private AppSettingService appSettingService;

    @InjectMocks
    private LubimyCzytacParser parser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFetchMetadata_ProviderDisabled() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Test Book")
            .build();

        // Mock disabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(false);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when provider is disabled");
        verify(appSettingService).getAppSettings();
    }

    @Test
    void testFetchMetadata_ProviderSettingsNull() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Test Book")
            .build();

        // Mock null settings
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(null);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when settings are null");
        verify(appSettingService).getAppSettings();
    }

    @Test
    void testFetchMetadata_EmptyQuery() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .build();
        // Empty query - no title or ISBN

        // Mock enabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(true);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when query is empty");
        verify(appSettingService).getAppSettings();
    }

    @Test
    @Disabled("Integration test - requires network access to LubimyCzytac.pl")
    void testFetchMetadata_Integration_RealBook() {
        // Given
        Book book = Book.builder()
            .title("Wiedźmin")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Wiedźmin")
            .author("Andrzej Sapkowski")
            .build();

        // Mock enabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(true);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should return results for real book");

        BookMetadata firstResult = results.getFirst();
        assertNotNull(firstResult.getTitle(), "Title should be present");
        assertNotNull(firstResult.getLubimyczytacId(), "LubimyCzytac ID should be present");
        assertTrue(firstResult.getAuthors() != null && !firstResult.getAuthors().isEmpty(),
            "Authors should be present");
    }
}
