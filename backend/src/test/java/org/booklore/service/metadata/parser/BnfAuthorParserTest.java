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
class BnfAuthorParserTest {

    @Mock private HttpClient httpClient;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private BnfAuthorParser parser;

    @BeforeEach
    void setUp() {
        parser = new BnfAuthorParser(httpClient, objectMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthorsMapsBnfAuthorityDataAndIsni() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "uri": {"value": "http://data.bnf.fr/ark:/12148/cb123#about"},
                        "name": {"value": "Ada Lovelace"},
                        "description": {"value": "Mathematician"},
                        "sameAs": {"value": "https://isni.org/isni/000000012146438X"}
                      }
                    ]
                  }
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        List<AuthorSearchResult> results = parser.searchAuthors("Ada Lovelace", "fr");

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getSource()).isEqualTo(AuthorMetadataSource.BNF);
            assertThat(result.getProviderId()).isEqualTo("http://data.bnf.fr/ark:/12148/cb123#about");
            assertThat(result.getName()).isEqualTo("Ada Lovelace");
            assertThat(result.getDescription()).isEqualTo("Mathematician");
            assertThat(result.getIsni()).isEqualTo("000000012146438X");
        });
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().uri().getHost()).isEqualTo("data.bnf.fr");
        assertThat(requestCaptor.getValue().uri().getPath()).isEqualTo("/sparql");
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAuthorByIdQueriesTheSelectedBnfAuthorityRecord() throws Exception {
        String json = """
                {
                  "results": {
                    "bindings": [
                      {
                        "uri": {"value": "http://data.bnf.fr/ark:/12148/cb123#about"},
                        "name": {"value": "Ada Lovelace"}
                      }
                    ]
                  }
                }
                """;
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        AuthorSearchResult result = parser.getAuthorById("http://data.bnf.fr/ark:/12148/cb123#about", "fr");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Ada Lovelace");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().uri().getHost()).isEqualTo("data.bnf.fr");
        assertThat(requestCaptor.getValue().uri().getPath()).isEqualTo("/sparql");
    }

    @Test
    void getAuthorByIdRejectsUrisOutsideBnf() {
        assertThat(parser.getAuthorById("https://example.com/author", "fr")).isNull();

        verifyNoInteractions(httpClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchAuthorsReturnsEmptyListWhenBnfIsUnavailable() throws Exception {
        doThrow(new IOException("Connection failed")).when(httpClient).send(any(HttpRequest.class), any());

        assertThat(parser.searchAuthors("Unknown Author", "fr")).isEmpty();
    }
}
