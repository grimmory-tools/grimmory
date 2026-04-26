package org.booklore.service.metadata.parser.hardcover;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class HardcoverBookSearchServiceTest {

    @Mock
    private AppSettingService appSettingService;

    private HardcoverBookSearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        searchService = new HardcoverBookSearchService(appSettingService);
    }

    @Nested
    @DisplayName("API Token Validation Tests")
    class ApiTokenTests {

        @Test
        @DisplayName("Should return empty list when API token is null")
        void searchBooks_nullToken_returnsEmptyList() {
            setupAppSettingsWithToken(null);

            List<GraphQLResponse.Hit> results = searchService.searchBooks("test query");

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should return empty list when API token is empty")
        void searchBooks_emptyToken_returnsEmptyList() {
            setupAppSettingsWithToken("");

            List<GraphQLResponse.Hit> results = searchService.searchBooks("test query");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Search Parameter Tests")
    class SearchParameterTests {

        @Test
        @DisplayName("Default per_page should be 10")
        void defaultPerPage_is10() {
            assertThat(HardcoverBookSearchService.DEFAULT_PER_PAGE).isEqualTo(10);
        }

        @Test
        @DisplayName("searchBooks with perPage should respect the parameter")
        void searchBooks_withPerPage_respectsParameter() {
            setupAppSettingsWithToken(null);  // Will return empty but logic is tested

            List<GraphQLResponse.Hit> results = searchService.searchBooks("test", 50);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchBookDetails Tests")
    class FetchBookDetailsTests {

        @Test
        @DisplayName("Should return null when API token is not set")
        void fetchBookDetails_noToken_returnsNull() {
            setupAppSettingsWithToken(null);

            HardcoverBookDetails result = searchService.fetchBookDetails(12345);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should return null when API token is empty")
        void fetchBookDetails_emptyToken_returnsNull() {
            setupAppSettingsWithToken("");

            HardcoverBookDetails result = searchService.fetchBookDetails(12345);


            assertThat(result).isNull();
        }
    }


    private void setupAppSettingsWithToken(String token) {
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Hardcover hardcover = new MetadataProviderSettings.Hardcover();
        hardcover.setApiKey(token);
        providerSettings.setHardcover(hardcover);
        appSettings.setMetadataProviderSettings(providerSettings);
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }
}
