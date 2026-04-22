package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleParserTest {

    @Mock
    private AppSettingService appSettingService;

    private ObjectMapper objectMapper;

    @Mock
    private HttpClient httpClient;

    private GoogleParser googleParser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectMapper = new ObjectMapper();
        
        // Mock AppSettings chain
        MetadataProviderSettings.Google googleSettings = new MetadataProviderSettings.Google();
        googleSettings.setLanguage("en");
        
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setGoogle(googleSettings);
        
        AppSettings appSettings = AppSettings.builder()
                .metadataProviderSettings(providerSettings)
                .build();
        
        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        googleParser = new GoogleParser(objectMapper, appSettingService, httpClient);
    }

    @Test
    void testFetchMetadata_IsbnSearch() throws Exception {
        // Mock ISBN Response (success) - Added author to pass relevance filter
        mockResponse("{\"totalItems\": 1, \"items\": [{\"volumeInfo\": {\"title\": \"ISBN Found\", \"authors\": [\"Test Author\"]}}]}");

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("1234567890")
                .build();

        List<BookMetadata> results = googleParser.fetchMetadata(Book.builder().build(), request);

        assertEquals(1, results.size());
        assertEquals("ISBN Found", results.get(0).getTitle());
        
        // Verify only 1 call was made (ISBN search)
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void testFetchMetadata_IsbnFail_FallbackToTitleAuthor() throws Exception {
        // Mock ISBN Response (empty) then Title+Author Response (success)
        mockResponseSequence(
            "{\"totalItems\": 0}", 
            "{\"totalItems\": 1, \"items\": [{\"volumeInfo\": {\"title\": \"TitleAuthor Found\", \"authors\": [\"Test Author\"]}}]}"
        );

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("1234567890")
                .title("My Title")
                .author("My Author")
                .build();

        List<BookMetadata> results = googleParser.fetchMetadata(Book.builder().build(), request);

        assertEquals(1, results.size());
        assertEquals("TitleAuthor Found", results.get(0).getTitle());

        // Verify 2 calls
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(2)).send(captor.capture(), any());
        
        List<HttpRequest> requests = captor.getAllValues();
        String uri = requests.get(1).uri().toString();
        assertTrue(uri.contains("inauthor") || uri.contains("inauthor%3A"));
        assertTrue(uri.contains("My") && uri.contains("Author"));
    }

    @Test
    void testFetchMetadata_AllFail_FallbackToTitleOnly() throws Exception {
        // ISBN fail, Title+Author fail, Title success
        mockResponseSequence(
            "{\"totalItems\": 0}", // ISBN
            "{\"totalItems\": 0}", // Title+Author
            "{\"totalItems\": 1, \"items\": [{\"volumeInfo\": {\"title\": \"Title Only Found\", \"authors\": [\"Test Author\"]}}]}"  // Title
        );

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("1234567890")
                .title("My Title")
                .author("My Author")
                .build();

        List<BookMetadata> results = googleParser.fetchMetadata(Book.builder().build(), request);

        assertEquals(1, results.size());
        assertEquals("Title Only Found", results.get(0).getTitle());

        // Verify 3 calls
        verify(httpClient, times(3)).send(any(), any());
    }

    // Helper to mock single response
    private void mockResponse(String jsonBody) throws IOException, InterruptedException {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockResponse.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
    }

    // Helper to mock sequence of responses
    private void mockResponseSequence(String... jsonBodies) throws IOException, InterruptedException {
        HttpResponse<String>[] responses = new HttpResponse[jsonBodies.length];
        for (int i = 0; i < jsonBodies.length; i++) {
            HttpResponse<String> mockInfo = mock(HttpResponse.class);
            when(mockInfo.body()).thenReturn(jsonBodies[i]);
            when(mockInfo.statusCode()).thenReturn(200);
            responses[i] = mockInfo;
        }
        
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(responses[0], Arrays.copyOfRange(responses, 1, responses.length));
    }
}
