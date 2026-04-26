package org.booklore.service.metadata.parser;

import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.enums.AuthorMetadataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudnexusAuthorParserTest {

    @Mock private HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private AudnexusAuthorParser parser;

    @BeforeEach
    void setUp() {
        parser = new AudnexusAuthorParser(httpClient, objectMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthors_returnsResultsOnSuccess() throws Exception {
        String json = """
                [
                  {"asin": "B000APZGGS", "name": "Stephen King", "description": "Horror author", "image": "https://img.com/king.jpg"},
                  {"asin": "B000AP1234", "name": "Stephen King Jr", "description": "Another one", "image": null}
                ]
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(json);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        List<AuthorSearchResult> results = parser.searchAuthors("Stephen King", "us");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAsin()).isEqualTo("B000APZGGS");
        assertThat(results.get(0).getSource()).isEqualTo(AuthorMetadataSource.AUDNEXUS);
        assertThat(results.get(0).getName()).isEqualTo("Stephen King");
        assertThat(results.get(0).getDescription()).isEqualTo("Horror author");
        assertThat(results.get(0).getImageUrl()).isEqualTo("https://img.com/king.jpg");
        assertThat(results.get(1).getAsin()).isEqualTo("B000AP1234");
        assertThat(results.get(1).getImageUrl()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthors_returnsEmptyListOnNon200() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        List<AuthorSearchResult> results = parser.searchAuthors("Nobody", "us");

        assertThat(results).isEmpty();
    }

    @Test
    void searchAuthors_returnsEmptyListOnException() throws Exception {
        doThrow(new IOException("Connection failed")).when(httpClient).send(any(HttpRequest.class), any());

        List<AuthorSearchResult> results = parser.searchAuthors("Fail", "us");

        assertThat(results).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthors_ignoresUnknownFields() throws Exception {
        String json = """
                [{"asin": "B000APZGGS", "name": "Author", "unknownField": "ignored", "anotherOne": 42}]
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(json);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        List<AuthorSearchResult> results = parser.searchAuthors("Author", "us");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAsin()).isEqualTo("B000APZGGS");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthors_sendsCorrectUrl() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[]");
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        parser.searchAuthors("Stephen King", "uk");

        verify(httpClient).send(requestCaptor.capture(), any());
        String uri = requestCaptor.getValue().uri().toString();
        assertThat(uri).contains("api.audnex.us/authors");
        assertThat(uri).contains("name=Stephen%20King");
        assertThat(uri).contains("region=uk");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuthorByAsin_returnsResultOnSuccess() throws Exception {
        String json = """
                {
                  "asin": "B000APZGGS",
                  "name": "Stephen King",
                  "description": "Master of horror fiction",
                  "image": "https://img.com/king.jpg",
                  "region": "us"
                }
                """;

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(json);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        AuthorSearchResult result = parser.getAuthorByAsin("B000APZGGS", "us");

        assertThat(result).isNotNull();
        assertThat(result.getAsin()).isEqualTo("B000APZGGS");
        assertThat(result.getSource()).isEqualTo(AuthorMetadataSource.AUDNEXUS);
        assertThat(result.getName()).isEqualTo("Stephen King");
        assertThat(result.getDescription()).isEqualTo("Master of horror fiction");
        assertThat(result.getImageUrl()).isEqualTo("https://img.com/king.jpg");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuthorByAsin_returnsNullOnNon200() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        AuthorSearchResult result = parser.getAuthorByAsin("INVALID", "us");

        assertThat(result).isNull();
    }

    @Test
    void getAuthorByAsin_returnsNullOnException() throws Exception {
        doThrow(new IOException("Timeout")).when(httpClient).send(any(HttpRequest.class), any());

        AuthorSearchResult result = parser.getAuthorByAsin("B000APZGGS", "us");

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuthorByAsin_sendsCorrectUrl() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"asin\":\"B000APZGGS\",\"name\":\"Test\"}");
        doReturn(mockResponse).when(httpClient).send(any(HttpRequest.class), any());

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        parser.getAuthorByAsin("B000APZGGS", "uk");

        verify(httpClient).send(requestCaptor.capture(), any());
        String uri = requestCaptor.getValue().uri().toString();
        assertThat(uri).contains("api.audnex.us/authors/B000APZGGS");
        assertThat(uri).contains("region=uk");
    }
}
