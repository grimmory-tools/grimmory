package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RanobeDbParserTest {
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AppSettingService appSettingService;

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private RanobeDbParser parser;

    @SuppressWarnings("unchecked")
    private HttpResponse<String> getResponse(int statusCode, String payload) {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(payload);

        return mockResponse;
    }

    private void mockResponse(String uri, int statusCode, String payload) throws IOException, InterruptedException {
        HttpResponse<String> response = getResponse(statusCode, payload);
        when(
                httpClient.<String>send(
                        argThat(arg -> arg != null && arg.uri().toString().contains(uri)),
                        any()
                )
        ).thenReturn(response);
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = "ranobedb/" + fixtureName + ".fixture";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
        assertThat(results).withFailMessage("Should return empty list when query is empty").isEmpty();
    }

    @Test
    void testFetchMetadata_Integration_RealBook() throws Exception {
        mockResponse("/books", 200, readFixture("books.json"));
        mockResponse("/book/47136", 200, readFixture("book.json"));
        mockResponse("/staff", 200, readFixture("staff.json"));

        // Given
        Book book = Book.builder()
            .title("seishun buta yarou")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("seishun buta yarou")
            .author("Kamoshida Hajime")
            .build();

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).withFailMessage("Should return results for real book").isNotEmpty();

        BookMetadata firstResult = results.getFirst();
        assertThat(firstResult.getTitle()).withFailMessage("Title should be present").isNotNull();
        assertThat(firstResult.getRanobedbId()).withFailMessage("RanobeDB ID should be present").isNotNull();

        var authors = firstResult.getAuthors();
        assertThat(authors).withFailMessage("Authors should be present").isNotEmpty();
    }

    @Test
    void testFetchMetadata_PrioritizesReleaseLanguage() throws Exception {
        mockResponse("/books", 200, readFixture("books.json"));
        mockResponse("/book/47136", 200, readFixture("book.json"));
        mockResponse("/staff", 200, readFixture("staff.json"));

        // Given
        Book book = Book.builder()
                .title("Example")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Example")
                .author("Example")
                .build();

        // When
        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        // Then
        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("Rascal Does Not Dream of a Beach Queen +");
    }
}
