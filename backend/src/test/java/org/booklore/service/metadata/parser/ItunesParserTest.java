package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookFile;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItunesParserTest {

    @Mock
    private AppSettingService appSettingService;

    private ObjectMapper objectMapper;

    @Mock
    private ItunesHttpClient itunesHttpClient;

    private ItunesParser itunesParser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        MetadataProviderSettings.Itunes itunesSettings = new MetadataProviderSettings.Itunes();
        itunesSettings.setEnabled(true);
        itunesSettings.setCountry("us");

        MetadataProviderSettings providerSettings = new MetadataProviderSettings();
        providerSettings.setItunes(itunesSettings);

        AppSettings appSettings = AppSettings.builder()
                .metadataProviderSettings(providerSettings)
                .build();

        lenient().when(appSettingService.getAppSettings()).thenReturn(appSettings);

        itunesParser = new ItunesParser(objectMapper, appSettingService, itunesHttpClient);
    }

    @Test
    void testFetchMetadata_Ebook_IsbnSearch() throws Exception {
        String jsonResponse = loadResource("/itunes/ebook_response.json");
        mockResponse(jsonResponse);

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("9780316073714")
                .build();

        List<BookMetadata> results = itunesParser.fetchMetadata(null, request);

        assertEquals(1, results.size());
        BookMetadata metadata = results.get(0);
        assertEquals("Blood of Elves", metadata.getTitle());
        assertEquals("Andrzej Sapkowski & Danusia Stok", metadata.getAuthors().get(0));
        assertEquals("track:357669161", metadata.getItunesId());
        assertEquals(4.5, metadata.getItunesRating());
        assertEquals(1984, metadata.getItunesReviewCount());
        assertTrue(metadata.getThumbnailUrl().contains("600x600bb.jpg"));
        assertEquals(MetadataProvider.Itunes, metadata.getProvider());
    }

    @Test
    void testFetchMetadata_Audiobook_Search() throws Exception {
        String jsonResponse = loadResource("/itunes/audiobook_response.json");
        mockResponse(jsonResponse);

        Book book = Book.builder()
                .primaryFile(BookFile.builder().bookType(BookFileType.AUDIOBOOK).build())
                .build();
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("The Last Wish")
                .author("Andrzej Sapkowski")
                .build();

        List<BookMetadata> results = itunesParser.fetchMetadata(book, request);

        assertEquals(1, results.size());
        BookMetadata metadata = results.get(0);
        assertEquals("O Último Desejo", metadata.getTitle());
        assertEquals("Andrzej Sapkowski", metadata.getAuthors().get(0));
        assertEquals("collection:1597317483", metadata.getItunesId());
        assertNull(metadata.getItunesRating());
        assertNull(metadata.getItunesReviewCount());
        assertEquals(MetadataProvider.Itunes, metadata.getProvider());
    }

    @Test
    void testFetchMetadata_Disabled() {
        MetadataProviderSettings.Itunes disabled = new MetadataProviderSettings.Itunes();
        disabled.setEnabled(false);
        MetadataProviderSettings settings = new MetadataProviderSettings();
        settings.setItunes(disabled);
        AppSettings disabledSettings = AppSettings.builder().metadataProviderSettings(settings).build();
        lenient().when(appSettingService.getAppSettings()).thenReturn(disabledSettings);

        List<BookMetadata> results = itunesParser.fetchMetadata(null,
                FetchMetadataRequest.builder().title("Test").build());

        assertTrue(results.isEmpty());
        verifyNoInteractions(itunesHttpClient);
    }

    @Test
    void testFetchDetailedMetadata() throws Exception {
        String jsonResponse = loadResource("/itunes/ebook_response.json");
        mockResponse(jsonResponse);

        BookMetadata metadata = itunesParser.fetchDetailedMetadata("track:357669161");

        assertNotNull(metadata);
        assertEquals("Blood of Elves", metadata.getTitle());
        assertEquals("track:357669161", metadata.getItunesId());
    }

    @Test
    void testFetchMetadata_RankingAndFiltering() throws Exception {
        String jsonBody = "{\n" +
                "  \"resultCount\": 2,\n" +
                "  \"results\": [\n" +
                "    {\n" +
                "      \"wrapperType\": \"track\",\n" +
                "      \"kind\": \"ebook\",\n" +
                "      \"trackId\": 12345,\n" +
                "      \"trackName\": \"The Walking Dead Vol. 26\",\n" +
                "      \"artistName\": \"Robert Kirkman\",\n" +
                "      \"trackViewUrl\": \"http://example.com/26\",\n" +
                "      \"artworkUrl100\": \"http://example.com/26.jpg\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"wrapperType\": \"track\",\n" +
                "      \"kind\": \"ebook\",\n" +
                "      \"trackId\": 67890,\n" +
                "      \"trackName\": \"The Walking Dead Vol. 16: A Larger World\",\n" +
                "      \"artistName\": \"Robert Kirkman\",\n" +
                "      \"trackViewUrl\": \"http://example.com/16\",\n" +
                "      \"artworkUrl100\": \"http://example.com/16.jpg\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        mockResponse(jsonBody);

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("The Walking Dead Vol. 16")
                .author("Robert Kirkman")
                .build();

        List<BookMetadata> results = itunesParser.fetchMetadata(null, request);

        // We expect only Vol. 16 to be returned, Vol. 26 should be filtered out
        assertEquals(1, results.size());
        assertEquals("The Walking Dead Vol. 16: A Larger World", results.get(0).getTitle());
        assertEquals("track:67890", results.get(0).getItunesId());
    }

    @Test
    void testFetchMetadata_BaptismOfFire() throws Exception {
        String jsonResponse = loadResource("/itunes/baptism_of_fire_response.json");
        mockResponse(jsonResponse);

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Baptism of Fire")
                .author("Andrzej Sapkowski")
                .build();

        List<BookMetadata> results = itunesParser.fetchMetadata(null, request);

        // We expect only "Baptism of Fire" to be returned.
        // Other Witcher books in the response (like Blood of Elves, Season of Storms) must be filtered out
        assertEquals(1, results.size());
        assertEquals("Baptism of Fire", results.get(0).getTitle());
        assertEquals("track:721822503", results.get(0).getItunesId());
    }

    @Test
    void testFetchMetadata_Transmetropolitan50() throws Exception {
        String jsonResponse = loadResource("/itunes/transmetropolitan_response.json");
        mockResponse(jsonResponse);

        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .title("Transmetropolitan, No. 50")
                .author("Warren Ellis")
                .build();

        List<BookMetadata> results = itunesParser.fetchMetadata(null, request);

        // We expect only volume 50 to match.
        // Other items like Transmetropolitan Book One, Book Two, Book Three, or other volumes/mismatches must be filtered out.
        assertEquals(1, results.size());
        assertEquals("Transmetropolitan (1997-) #50", results.get(0).getTitle());
        assertEquals("track:875066994", results.get(0).getItunesId());
    }

    @Test
    void testResizeArtworkUrl() {
        assertEquals("https://example.com/image/600x600bb.jpg", ItunesParser.resizeArtworkUrl("https://example.com/image/100x100bb.jpg", 600, 600));
        assertEquals("https://example.com/image/1000x1000bb.jpg", ItunesParser.resizeArtworkUrl("https://example.com/image/100x100.jpg", 1000, 1000));
        assertEquals("https://example.com/image/600x600bb.jpg", ItunesParser.resizeArtworkUrl("https://example.com/image/30x30bb.jpg", 600, 600));
        assertEquals("https://example.com/image/no-dimension.jpg", ItunesParser.resizeArtworkUrl("https://example.com/image/no-dimension.jpg", 600, 600));
        assertNull(ItunesParser.resizeArtworkUrl(null, 600, 600));
    }

    private void mockResponse(String jsonBody) throws IOException, InterruptedException {
        when(itunesHttpClient.executeGet(any(), any(), any())).thenReturn(jsonBody);
    }

    private String loadResource(String path) throws IOException {
        return new String(Objects.requireNonNull(getClass().getResourceAsStream(path)).readAllBytes(), StandardCharsets.UTF_8);
    }
}
