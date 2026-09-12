package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DoubanBookParserTest {
    @Mock
    private AppSettingService mockAppSettingService;

    @Spy
    private ObjectMapper objectMapper = JsonMapper.shared();

    @InjectMocks
    private DoubanBookParser doubanBookParser;

    private MockedStatic<Jsoup> mockJsoup;

    private String readFixture(String fixtureName) throws IOException {
        String filename = "douban/" + fixtureName + ".fixture";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private AppSettings getAppSettings() {
        MetadataProviderSettings.Douban doubanSettings = new MetadataProviderSettings.Douban();
        doubanSettings.setEnabled(true);

        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();
        metadataProviderSettings.setDouban(doubanSettings);

        MetadataPublicReviewsSettings metadataPublicReviewsSettings = MetadataPublicReviewsSettings.builder()
                .providers(Set.of())
                .build();

        return AppSettings
                .builder()
                .metadataPublicReviewsSettings(metadataPublicReviewsSettings)
                .metadataProviderSettings(metadataProviderSettings)
                .build();
    }

    private Book getBook() {
        return getBook(null);
    }

    private Book getBook(String doubanId) {
        BookMetadata bookMetadata = BookMetadata.builder()
                .doubanId(doubanId)
                .build();

        return Book.builder()
                .title("Example")
                .metadata(bookMetadata)
                .build();
    }

    private Connection getConnection(Document document) throws IOException {
        Connection mockConnection = mock(Connection.class);

        Connection.Response mockResponse = mock(Connection.Response.class);

        when(mockConnection.header(any(String.class), any(String.class))).thenReturn(mockConnection);
        when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
        when(mockConnection.ignoreContentType(anyBoolean())).thenReturn(mockConnection);
        when(mockConnection.followRedirects(anyBoolean())).thenReturn(mockConnection);
        when(mockConnection.maxBodySize(anyInt())).thenReturn(mockConnection);
        when(mockConnection.method(any(Connection.Method.class))).thenReturn(mockConnection);
        when(mockConnection.execute()).thenReturn(mockResponse);

        when(mockResponse.parse()).thenReturn(document);
        when(mockResponse.url()).thenReturn(URI.create("https://example.com/").toURL());

        return mockConnection;
    }

    private void mockJsoupConnect(String url, String html) throws Exception {
        Document document = Parser.parse(html, "");
        Connection connection = getConnection(document);

        mockJsoup.when(() -> Jsoup.connect(url))
                .thenReturn(connection);
    }

    @BeforeEach
    public void setup() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings());

        mockJsoup = mockStatic(Jsoup.class);
    }

    @AfterEach
    void tearDown() {
        mockJsoup.close();
    }

    @Test
    public void parsesEmptySearchResults() throws Exception {
        mockJsoupConnect("https://search.douban.com/book/subject_search?search_text=Example", readFixture("empty-search.html"));

        var book = getBook();
        var fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        var results = doubanBookParser.fetchMetadata(book, fetchMetadataRequest);

        assertThat(results).hasSize(0);
    }

    @Test
    public void parsesSearchResults() throws Exception {
        mockJsoupConnect("https://search.douban.com/book/subject_search?search_text=Example", readFixture("match-search.html"));
        mockJsoupConnect("https://book.douban.com/subject/36939359", readFixture("book.html"));
        mockJsoupConnect("https://book.douban.com/subject/35297655", readFixture("book.html"));
        mockJsoupConnect("https://book.douban.com/subject/26328534", readFixture("book.html"));

        var book = getBook();
        var fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        var results = doubanBookParser.fetchMetadata(book, fetchMetadataRequest);

        assertThat(results).hasSize(3);
    }
}
