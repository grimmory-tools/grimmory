package org.booklore.service.kobo;

import org.booklore.model.dto.BookloreSyncToken;
import org.booklore.model.dto.kobo.KoboHeaders;
import org.booklore.util.kobo.BookloreSyncTokenGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KoboServerProxyTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BookloreSyncTokenGenerator bookloreSyncTokenGenerator;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Mock
    private HttpResponse<byte[]> httpResponseBytes;

    @InjectMocks
    private KoboServerProxy koboServerProxy;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() throws Exception {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("GET");
        mockRequest.setRequestURI("/api/kobo/v1/library/sync");
        
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        Field httpClientField = KoboServerProxy.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(koboServerProxy, httpClient);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setupSuccessfulProxyResponse() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Collections.emptyMap(),
                (k, v) -> true
        ));
        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(httpResponse);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Authorization", "authorization" })
    void proxyCurrentRequest_withSquareBracketsInQueryParams_shouldIncludeAuthHeaders(String authHeader) throws Exception {
        mockRequest.setQueryString("Filter=ALL");
        mockRequest.addHeader("User-Agent", "Kobo/1.0");
        mockRequest.addHeader(authHeader, "jwt.looking.token");
        setupSuccessfulProxyResponse();

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).<String>send(captor.capture(), any());
        assertThat(captor.getValue().headers().map().get(authHeader)).isNotNull();
    }

    @Test
    void proxyCurrentRequest_withSquareBracketsInQueryParams_shouldHandleSuccessfully() throws Exception {
        // Validates fix for: IllegalArgumentException: Invalid character '[' for QUERY_PARAM
        String problematicQuery = "Filters=[%7BKey:TestKey,ETag:W/TestEtagValue123%7D]";
        mockRequest.setQueryString(problematicQuery);
        mockRequest.addHeader("User-Agent", "Kobo/1.0");
        setupSuccessfulProxyResponse();

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(httpClient).<String>send(any(HttpRequest.class), any());
    }

    @Test
    void proxyCurrentRequest_withVariousEncodedQueryParams_shouldHandleSuccessfully() throws Exception {
        String[] testQueries = {
            "Filters=%5B%7BKey:TestKey%7D%5D",  // Properly encoded brackets
            "Filter=ALL&DownloadUrlFilter=Generic,Android&PrioritizeRecentReads=true",  // Real Kobo params
            "page_index=0&page_size=50&Filters=%7B%7D"  // URL-encoded JSON
        };

        for (String query : testQueries) {
            mockRequest.setQueryString(query);
            setupSuccessfulProxyResponse();

            ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        
        verify(httpClient, times(testQueries.length)).<String>send(any(HttpRequest.class), any());
    }

    @Test
    void proxyCurrentRequest_withSyncToken_shouldPreserveAndUpdateToken() throws Exception {
        mockRequest.setQueryString("Filter=ALL");
        mockRequest.addHeader(KoboHeaders.X_KOBO_SYNCTOKEN, "original-token");

        BookloreSyncToken mockSyncToken = BookloreSyncToken.builder()
                .rawKoboSyncToken("kobo-sync-token")
                .ongoingSyncPointId("1")
                .lastSuccessfulSyncPointId("0")
                .build();

        when(bookloreSyncTokenGenerator.fromRequestHeaders(any())).thenReturn(mockSyncToken);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of(KoboHeaders.X_KOBO_SYNCTOKEN, java.util.List.of("new-kobo-token")),
                (k, v) -> true
        ));
        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));
        when(bookloreSyncTokenGenerator.toBase64(any())).thenReturn("encoded-sync-token");
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(httpResponse);

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, true);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(bookloreSyncTokenGenerator).toBase64(any(BookloreSyncToken.class));
    }

    @Test
    void proxyCurrentRequest_shouldPreserveKoboHeaders() throws Exception {
        mockRequest.addHeader("X-Kobo-DeviceId", "device123");
        mockRequest.addHeader("X-Kobo-UserKey", "user456");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{}");
        when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of("X-Kobo-Response", java.util.List.of("test")),
                (k, v) -> true
        ));
        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(httpResponse);

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);

        assertThat(response).isNotNull();
        assertThat(response.getHeaders().getFirst("X-Kobo-Response")).isNotNull();
    }

    @Test
    void proxyCurrentRequest_withPostBody_shouldSerializeBody() throws Exception {
        mockRequest.setMethod("POST");
        Object requestBody = new Object();
        
        when(objectMapper.writeValueAsString(requestBody)).thenReturn("{\"test\":\"value\"}");
        setupSuccessfulProxyResponse();

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(requestBody, false);

        assertThat(response).isNotNull();
        verify(objectMapper).writeValueAsString(requestBody);
    }

    @Test
    void proxyCurrentRequest_with400Response_shouldPreserveStatusCode() throws Exception {
        mockRequest.setQueryString("ExcludeOwned=false&PageSize=100");

        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("{\"error\":\"Bad Request\"}");
        when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Collections.emptyMap(),
                (k, v) -> true
        ));
        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenReturn(httpResponse);

        ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void proxyCurrentRequest_withMaliciousQueryParams_shouldHandleSafely() throws Exception {
        String[] maliciousQueries = {
            "param1=value1%0D%0AHost:evil.com",  // CRLF injection attempt
            "text=%3Cscript%3Ealert('xss')%3C/script%3E&other=%22quotes%22"  // XSS attempt
        };

        for (String query : maliciousQueries) {
            mockRequest.setQueryString(query);
            setupSuccessfulProxyResponse();

            ResponseEntity<JsonNode> response = koboServerProxy.proxyCurrentRequest(null, false);
            
            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void getKoboCDNCoverUri_shouldGenerateUri() {
        URI actual = koboServerProxy.getKoboCDNCoverUri("example", 1, 2, false);

        assertThat(actual.toString()).isEqualTo("https://cdn.kobo.com/book-images/example/1/2/false/image.jpg");
    }

    @Test
    void getKoboCDNCoverUri_shouldEscapeImageComponent() {
        URI actual = koboServerProxy.getKoboCDNCoverUri("foo/bar", 1, 2, false);

        assertThat(actual.toString()).isEqualTo("https://cdn.kobo.com/book-images/foo%2Fbar/1/2/false/image.jpg");
    }
}
