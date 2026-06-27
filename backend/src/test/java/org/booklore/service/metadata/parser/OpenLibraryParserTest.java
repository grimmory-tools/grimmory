
package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.service.SleepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OpenLibraryParserTest {
    @Mock
    private HttpClient httpClient;

    @Mock
    private SleepService sleepService;

    private OpenLibraryParser parser;

    @BeforeEach
    void setUp() throws IOException {
        parser = new OpenLibraryParser(
                httpClient,
                new ObjectMapper(),
                sleepService
        );
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("openlibrary", fixtureName + ".fixture").toString();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> getMockResponse(int statusCode, String response) {
        HttpResponse<InputStream> httpResponse = (HttpResponse<InputStream>) mock(HttpResponse.class);

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
                        argThat(r -> r != null && r.uri().toString().startsWith(urlPrefix)),
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

        // Two expected URLs
        mockHttpClientResponse("https://openlibrary.org/search.json", 200, readFixture("example-search.json"));
        mockHttpClientResponse("https://openlibrary.org/works/OL261794W.json", 200, readFixture("example-work.json"));
        mockHttpClientResponse("https://openlibrary.org/books/OL27284158M.json", 200, readFixture("example-edition.json"));
        mockHttpClientResponse("https://openlibrary.org/authors/OL142238A.json", 200, readFixture("example-author.json"));

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).as("Should return results for real book").isNotEmpty();

        BookMetadata result = results.getFirst();
        assertThat(result.getTitle()).isEqualTo("A Clockwork Orange");
        assertThat(result.getIsbn10()).isEqualTo("1856132668");
        assertThat(result.getIsbn13()).isEqualTo("9781856132664");
        assertThat(result.getGoodreadsId()).isEqualTo("59769676");
        assertThat(result.getAuthors()).isNotNull();
        assertThat(result.getAuthors()).hasSize(1);
        assertThat(result.getAuthors().getFirst()).isEqualTo("Anthony Burgess");

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertThat(result.getDescription()).startsWith("A Clockwork Orange is a dystopian satirical black comedy novel");
        assertThat(result.getDescription()).hasSize(1307);
    }

    @Test
    void testFetchMetadata_parsesBookWithComplexDescriptionAndSeries() throws Exception {
        // Given
        Book book = Book.builder()
                .title("")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Lord of the Rings")
                .build();

        // Two expected URLs
        mockHttpClientResponse("https://openlibrary.org/search.json", 200, readFixture("lotr-search.json"));
        mockHttpClientResponse("https://openlibrary.org/works/OL27448W.json", 200, readFixture("lotr-work.json"));
        mockHttpClientResponse("https://openlibrary.org/series/OL330052L.json", 200, readFixture("lotr-series.json"));
        mockHttpClientResponse("https://openlibrary.org/books/OL51711484M.json", 200, readFixture("lotr-edition.json"));
        mockHttpClientResponse("https://openlibrary.org/authors/OL26320A.json", 200, readFixture("lotr-author.json"));

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertThat(results).isNotNull();
        assertThat(results).as("Should return results for real book").isNotEmpty();

        BookMetadata result = results.getFirst();
        assertThat(result.getTitle()).isEqualTo("The Lord of the Rings");
        assertThat(result.getIsbn10()).isEqualTo("0618343997");
        assertThat(result.getIsbn13()).isNull();
        assertThat(result.getGoodreadsId()).isNull();
        assertThat(result.getAuthors()).isNotNull();
        assertThat(result.getAuthors()).hasSize(1);
        assertThat(result.getAuthors().getFirst()).isEqualTo("J.R.R. Tolkien");

        assertThat(result.getSeriesName()).isEqualTo("The Lord of the Rings");
        assertThat(result.getSeriesNumber()).isEqualTo(1);
        assertThat(result.getSeriesTotal()).isEqualTo(3);

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertThat(result.getDescription()).startsWith("Originally published from 1954 through 1956");
        assertThat(result.getDescription()).hasSize(2137);
    }

    @Test
    void testFetchMetadata_appliesRateLimit() throws Exception {
        // Given
        Book book = Book.builder()
                .title("")
                .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Lord of the Rings")
                .build();

        // Two expected URLs
        mockHttpClientResponse("https://openlibrary.org/search.json", 200, readFixture("lotr-search.json"));
        mockHttpClientResponse("https://openlibrary.org/works/OL27448W.json", 200, readFixture("lotr-work.json"));
        mockHttpClientResponse("https://openlibrary.org/series/OL330052L.json", 200, readFixture("lotr-series.json"));
        mockHttpClientResponse("https://openlibrary.org/books/OL51711484M.json", 200, readFixture("lotr-edition.json"));
        mockHttpClientResponse("https://openlibrary.org/authors/OL26320A.json", 200, readFixture("lotr-author.json"));

        // When
        parser.fetchMetadata(book, request);
        parser.fetchMetadata(book, request);
        parser.fetchMetadata(book, request);

        verify(sleepService, times(2)).sleep(anyLong());
    }
}