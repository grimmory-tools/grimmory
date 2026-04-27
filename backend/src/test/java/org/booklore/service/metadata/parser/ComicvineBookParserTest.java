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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ComicvineBookParserTest {
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HttpClient httpClient;

    @Mock
    private AppSettingService mockAppSettingService;

    @InjectMocks
    private ComicvineBookParser comicvineBookParser;

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
                argThat(arg -> arg != null && arg.uri().toString().startsWith(("https://comicvine.gamespot.com/api" + uri))),
                any()
            )
        ).thenReturn(response);
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("comicvinebookparser", fixtureName + ".fixture").toString();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Book getBook(String comicvineId) {
        BookMetadata bookMetadata = BookMetadata.builder()
                .comicvineId(comicvineId)
                .build();

        return Book.builder()
                .title("Example")
                .metadata(bookMetadata)
                .build();
    }

    private AppSettings getAppSettings() {
        MetadataProviderSettings.Comicvine comicvineSettings = new MetadataProviderSettings.Comicvine();
        comicvineSettings.setEnabled(true);
        comicvineSettings.setApiKey("example");

        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();
        metadataProviderSettings.setComicvine(comicvineSettings);

        return AppSettings.builder()
                .metadataProviderSettings(metadataProviderSettings)
                .build();
    }

    @BeforeEach
    public void setup() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings());
    }

    @Test
    public void fetchTopMetadata_getsTitle() throws IOException, InterruptedException {
        mockResponse(
                "/search/?api_key=example&format=json&resources=volume,issue&query=Example&",
                200,
                readFixture("search.json")
        );

        mockResponse(
                "/issue/4000-60593/",
                200,
                readFixture("issue.json")
        );

        Book book = getBook("EXAMPLE");
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Example")
                .build();

        BookMetadata metadata = comicvineBookParser.fetchTopMetadata(book, request);

        assertNotNull(metadata);
        assertEquals("The Example", metadata.getTitle());
    }

    @Test
    public void fetchTopMetadata_parsesSeriesInfoFromTitle() throws IOException, InterruptedException {
        mockResponse(
                "/volumes/?api_key=example&format=json&filter=name:The%20Example&",
                200,
                readFixture("search.json")
        );

        mockResponse(
                "/issues/?api_key=example&format=json&filter=volume:60593,issue_number:1&",
                200,
                readFixture("issues.json")
        );

        Book book = getBook("EXAMPLE");
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("The Example #1")
                .build();

        BookMetadata metadata = comicvineBookParser.fetchTopMetadata(book, request);

        assertNotNull(metadata);
        assertEquals("The Example", metadata.getSeriesName());
        assertEquals(1.0f, metadata.getSeriesNumber());
    }
}
