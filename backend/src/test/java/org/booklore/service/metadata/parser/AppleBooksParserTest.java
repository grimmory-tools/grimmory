
package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AppleBooksParserTest {
    @Mock
    private AppSettingService appSettingService;

    @Mock
    private HttpClient httpClient;

    private AppleBooksParser parser;

    @BeforeEach
    void setUp() throws IOException {
        parser = new AppleBooksParser(
                new ObjectMapper(),
                appSettingService,
                httpClient
        );
    }


    private void mockSettings(boolean enabled, String country) {
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.AppleBooks appleBooks = new MetadataProviderSettings.AppleBooks();
        appleBooks.setEnabled(enabled);
        appleBooks.setCountry(country);
        providerSettings.setAppleBooks(appleBooks);
        appSettings.setMetadataProviderSettings(providerSettings);
        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = String.format("applebooks/%s.fixture", fixtureName);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> getMockResponse(int statusCode, String response) {
        HttpResponse<String> httpResponse = (HttpResponse<String>) mock(HttpResponse.class);

        when(httpResponse.statusCode()).thenReturn(statusCode);

        if (response != null) {
            when(httpResponse.body()).thenAnswer(
                    (_) -> new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))
            );
        }

        return httpResponse;
    }

    private void mockHttpClientResponse(String urlPrefix, int statusCode, String response) throws Exception {
        when(
                httpClient.<String>send(
                        argThat(r -> r.uri().toString().startsWith(urlPrefix)),
                        any()
                )
        ).thenAnswer((_) -> getMockResponse(statusCode, response));
    }

    @Test
    void testFetchMetadata_EmptyQuery() {
        Book book = Book.builder()
                .title("Test Book")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .build();

        mockSettings(true, "US");

        List<BookMetadata> results = parser.fetchMetadata(book, request);

        assertThat(results).isNotNull();
        assertThat(results).as("Should return empty list when query is empty").isEmpty();
    }

    @Test
    void testFetchMetadata_parsesBook() throws Exception {
        // Given
        Book book = Book.builder()
                .title("A Clockwork Orange")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("A Clockwork Orange")
                .author("Anthony Burgess")
                .build();

        // Mock enabled provider
        mockSettings(true, "US");

        // Expected URL
        mockHttpClientResponse("https://itunes.apple.com/search", 200, readFixture("example-search.json"));

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).as("Should return results for real book").isNotEmpty();

        BookMetadata result = results.getFirst();
        assertThat(result.getTitle()).isEqualTo("A Clockwork Orange");
        assertThat(result.getAuthors()).isNotNull();
        assertThat(result.getAuthors()).hasSize(1);
        assertThat(result.getAuthors().getFirst()).isEqualTo("Anthony Burgess");

        assertThat(result.getApplebooksId()).isEqualTo("track:1519750509");
        assertThat(result.getApplebooksRating()).isEqualTo(4.5);
        assertThat(result.getApplebooksReviewCount()).isEqualTo(152);

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertThat(result.getDescription()).startsWith(
                "Great Music, it said, and Great Poetry would like quieten Modern Youth down " +
                "and make Modern Youth more Civilized. Civilized my syphilised yarbles."
        );
        assertThat(result.getDescription()).hasSize(511);
    }
}
