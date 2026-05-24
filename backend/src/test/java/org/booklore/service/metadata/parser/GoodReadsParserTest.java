
package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoodReadsParserTest {
    @Mock
    private AppSettingService appSettingService;

    @Mock
    private HttpClient httpClient;

    private MockedStatic<Jsoup> mockJsoup;

    @InjectMocks
    private GoodReadsParser parser;

    private String exampleSearchJsonFixture;

    private String exampleBookHtmlFixture;

    @BeforeEach
    void setUp() throws IOException {
        exampleSearchJsonFixture = readFixture("example-search.json");
        exampleBookHtmlFixture = readFixture("example-book.html");



        mockJsoup = mockStatic(Jsoup.class);
    }

    @AfterEach
    void tearDown() {
        mockJsoup.close();
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
        when(httpResponse.body()).thenReturn(response);

        return httpResponse;
    }

    private void mockHttpClientResponse(String urlPrefix, int statusCode, String response) throws Exception {
        when(
                httpClient.<String>send(
                        argThat(r -> r.uri().toString().startsWith(urlPrefix)),
                        eq(HttpResponse.BodyHandlers.ofString())
                )
        ).thenAnswer((_) -> getMockResponse(statusCode, response));
    }

    private void mockJsoupResponse(String urlPrefix, String response) throws Exception {
        Connection mockConnection = mock(Connection.class);

        when(mockConnection.header(anyString(), anyString())).thenReturn(mockConnection);
        when(mockConnection.method(any())).thenReturn(mockConnection);

        // There may be a better way to get the parse to work here.
        // However, this was the quickest and simplest way I could find.
        mockJsoup.when(() -> Jsoup.parse(response)).thenCallRealMethod();

        when(mockConnection.get()).thenAnswer(i -> Jsoup.parse(response));

        mockJsoup.when(() -> Jsoup.connect(startsWith(urlPrefix))).thenReturn(mockConnection);
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
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when query is empty");
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

        // Two expected URLs
        mockHttpClientResponse("https://www.goodreads.com/book/auto_complete", 200, exampleSearchJsonFixture);
        mockJsoupResponse("https://www.goodreads.com/book/show/", exampleBookHtmlFixture);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should return results for real book");

        BookMetadata result = results.getFirst();
        assertEquals("A Clockwork Orange", result.getTitle());
        assertEquals("0393341763", result.getIsbn10());
        assertEquals("9780393341768", result.getIsbn13());
        assertEquals("41817486", result.getGoodreadsId());
        assertNotNull(result.getAuthors());
        assertEquals(1, result.getAuthors().size());
        assertEquals("Anthony Burgess", result.getAuthors().getFirst());

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertEquals("In Anthony Burgess's influen", result.getDescription().substring(0, 28));
        assertEquals(511, result.getDescription().length());
    }
}
