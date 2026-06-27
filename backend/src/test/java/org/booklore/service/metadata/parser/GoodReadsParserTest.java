
package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoodReadsParserTest {
    @Mock
    private AppSettingService appSettingService;

    @Mock
    private HttpClient httpClient;

    private GoodReadsParser parser;

    private String exampleSearchJsonFixture;

    private String exampleGraphqlResponseFixture;

    @BeforeEach
    void setUp() throws IOException {
        parser = new GoodReadsParser(
                httpClient,
                appSettingService,
                new ObjectMapper()
        );

        exampleSearchJsonFixture = readFixture("example-search.json");
        exampleGraphqlResponseFixture = readFixture("example-graphql-response.json");
    }

    private void mockSettings(boolean enabled) {
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Goodreads goodreads = new MetadataProviderSettings.Goodreads();
        goodreads.setEnabled(enabled);
        providerSettings.setGoodReads(goodreads);
        appSettings.setMetadataProviderSettings(providerSettings);

        MetadataPublicReviewsSettings.ReviewProviderConfig provider = MetadataPublicReviewsSettings.ReviewProviderConfig.builder()
                .provider(MetadataProvider.GoodReads)
                .enabled(enabled)
                .build();

        MetadataPublicReviewsSettings reviewSettings = MetadataPublicReviewsSettings.builder()
                .providers(Set.of(provider))
                        .build();

        appSettings.setMetadataPublicReviewsSettings(reviewSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("goodreads", fixtureName + ".fixture").toString();

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
            when(httpResponse.body()).thenReturn(response);
        }

        return httpResponse;
    }

    @SuppressWarnings("unchecked")
    private void mockHttpClientResponse(String urlPrefix, int statusCode, String response) throws Exception {
        when(
                httpClient.<String>send(
                        argThat(r -> r != null && r.uri() != null && r.uri().toString().startsWith(urlPrefix)),
                        any()
                )
        ).thenAnswer((_) -> getMockResponse(statusCode, response));
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

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
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
        mockSettings(true);

        // Two expected URLs: search + GraphQL
        mockHttpClientResponse("https://www.goodreads.com/book/auto_complete", 200, exampleSearchJsonFixture);
        mockHttpClientResponse("https://kxbwmqov6jgg3daaamb744ycu4.appsync-api.us-east-1.amazonaws.com/graphql", 200, exampleGraphqlResponseFixture);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).as("Should return results for real book").isNotEmpty();

        BookMetadata result = results.getFirst();
        assertThat(result.getTitle()).isEqualTo("A Clockwork Orange");
        assertThat(result.getIsbn10()).isEqualTo("0393341763");
        assertThat(result.getIsbn13()).isEqualTo("9780393341768");
        assertThat(result.getGoodreadsId()).isEqualTo("41817486");
        assertThat(result.getAuthors()).isNotNull();
        assertThat(result.getAuthors()).hasSize(1);
        assertThat(result.getAuthors().getFirst()).isEqualTo("Anthony Burgess");

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertThat(result.getDescription()).startsWith("In Anthony Burgess's influential");
        assertThat(result.getDescription()).hasSize(511);
    }

    @Test
    void testFetchMetadata_withRateLimitingError() throws Exception {
        // Given
        Book book = Book.builder()
                .title("A Clockwork Orange")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("A Clockwork Orange")
                .author("Anthony Burgess")
                .build();

        // Two expected URLs
        mockHttpClientResponse("https://www.goodreads.com/book/auto_complete", 429, null);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).as("Should not return results").isEmpty();
    }
}
