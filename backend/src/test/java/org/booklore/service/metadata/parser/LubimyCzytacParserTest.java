package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LubimyCzytacParserTest {

    @Mock
    private AppSettingService appSettingService;

    private MockedStatic<Jsoup> mockJsoup;

    @InjectMocks
    private LubimyCzytacParser parser;

    private String exampleSearchHtmlFixture;

    private String exampleBookHtmlFixture;

    @BeforeEach
    void setUp() throws IOException {
        exampleSearchHtmlFixture = readFixture("example-search.html");
        exampleBookHtmlFixture = readFixture("example-book.html");

        mockJsoup = mockStatic(Jsoup.class);

    }

    @AfterEach
    void tearDown() {
        mockJsoup.close();
    }

    @Test
    void testFetchMetadata_ProviderDisabled() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Test Book")
            .build();

        // Mock disabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(false);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when provider is disabled");
        verify(appSettingService).getAppSettings();
    }

    @Test
    void testFetchMetadata_ProviderSettingsNull() {
        // Given
        Book book = Book.builder()
            .title("Test Book")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Test Book")
            .build();

        // Mock null settings
        AppSettings appSettings = new AppSettings();
        appSettings.setMetadataProviderSettings(null);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when settings are null");
        verify(appSettingService).getAppSettings();
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

        // Mock enabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(true);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Should return empty list when query is empty");
        verify(appSettingService).getAppSettings();
    }

    @Test
    void testFetchMetadata_parsesBook() throws Exception {
        // Given
        Book book = Book.builder()
            .title("Wiedźmin")
            .build();

        FetchMetadataRequest request = FetchMetadataRequest.builder()
            .title("Sklepy cynamonowe")
            .author("Bruno Schulz")
            .build();

        // Mock enabled provider
        AppSettings appSettings = new AppSettings();
        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        MetadataProviderSettings.Lubimyczytac lubimyCzytac = new MetadataProviderSettings.Lubimyczytac();
        lubimyCzytac.setEnabled(true);
        providerSettings.setLubimyczytac(lubimyCzytac);
        appSettings.setMetadataProviderSettings(providerSettings);

        // Two expected URLs
        mockResponse("https://lubimyczytac.pl/szukaj/ksiazki", exampleSearchHtmlFixture);
        mockResponse("https://lubimyczytac.pl/ksiazka/", exampleBookHtmlFixture);

        when(appSettingService.getAppSettings()).thenReturn(appSettings);

        // When
        List<BookMetadata> results = parser.fetchMetadata(book, request);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should return results for real book");

        BookMetadata result = results.getFirst();
        assertEquals("Sklepy cynamonowe", result.getTitle());
        assertNull(result.getIsbn10());
        assertEquals("9788384258149", result.getIsbn13());
        assertEquals("5223841", result.getLubimyczytacId());
        assertNotNull(result.getAuthors());
        assertEquals(1, result.getAuthors().size());
        assertEquals("Bruno Schulz", result.getAuthors().getFirst());

        // The description is very long, but we need to make sure we're in the right ballpark.
        assertEquals("Jedno z najoryginalniejszych", result.getDescription().substring(0, 28));
        assertEquals(827, result.getDescription().length());
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("lubimyczytac", fixtureName + ".fixture").toString();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void mockResponse(String urlPrefix, String response) throws Exception {
        Connection mockConnection = mock(Connection.class);

        when(mockConnection.userAgent(any())).thenReturn(mockConnection);
        when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);

        // There may be a better way to get the parse to work here.
        // However, this was the quickest and simplest way I could find.
        mockJsoup.when(() -> Jsoup.parse(response)).thenCallRealMethod();
        Document document = Jsoup.parse(response);

        when(mockConnection.get()).thenReturn(document);

        mockJsoup.when(() -> Jsoup.connect(startsWith(urlPrefix))).thenReturn(mockConnection);
    }
}
