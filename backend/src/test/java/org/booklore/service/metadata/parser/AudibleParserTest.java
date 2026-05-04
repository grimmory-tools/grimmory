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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AudibleParserTest {
    @Mock private AppSettingService mockAppSettingService;

    private AudibleParser audibleParser;

    private MockedStatic<Jsoup> mockJsoup;

    private AppSettings getAppSettings(String domain) {
        MetadataProviderSettings.Audible audibleSettings = new MetadataProviderSettings.Audible();
        audibleSettings.setEnabled(true);
        audibleSettings.setDomain(domain);

        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();
        metadataProviderSettings.setAudible(audibleSettings);

        MetadataPublicReviewsSettings publicReviewsSettings = MetadataPublicReviewsSettings.builder()
                .providers(Collections.emptySet())
                .build();

        return AppSettings
                .builder()
                .metadataPublicReviewsSettings(publicReviewsSettings)
                .metadataProviderSettings(metadataProviderSettings)
                .build();
    }

    private Book getBook(String asin) {
        BookMetadata bookMetadata = BookMetadata.builder()
                .asin(asin)
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
        when(mockConnection.method(any(Connection.Method.class))).thenReturn(mockConnection);
        when(mockConnection.timeout(anyInt())).thenReturn(mockConnection);
        when(mockConnection.execute()).thenReturn(mockResponse);

        when(mockResponse.parse()).thenReturn(document);

        return mockConnection;
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = "audible/" + fixtureName + ".fixture";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void mockJsoupConnect(String url, String html) throws Exception {
        Document document = Parser.parse(html, "");
        Connection connection = getConnection(document);

        mockJsoup.when(() -> Jsoup.connect(url))
                .thenReturn(connection);
    }

    private void mockASINSearch(String keyword) throws Exception {
        mockJsoupConnect(
                "https://www.audible.com/search?keywords=" + keyword,
                """
                <html><body>
                <div data-widget="productList">
                <a href="https://www.audible.com/pd/something/SEARCHASIN">Example</a>
                </div>
                </body></html>
                """
        );

    }

    private void mockExampleProductPage() throws Exception {
        mockJsoupConnect(
                "https://www.audible.com/pd/B0FQSDKVQ8",
                readFixture("example-book-B0FQSDKVQ8.html")
        );
    }

    @BeforeEach
    public void setup() throws Exception {
        audibleParser = new AudibleParser(
                mockAppSettingService,
                new ObjectMapper()
        );

        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings("com"));

        mockJsoup = mockStatic(Jsoup.class);
    }

    @AfterEach
    void tearDown() {
        mockJsoup.close();
    }

    @Test
    public void fetchTopMetadata_usesAsinFromBookWhenAvailable() throws Exception {
        mockJsoupConnect("https://www.audible.com/pd/EXAMPLESKU", "<html />");

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.com/pd/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_useDomain() throws Exception {
        mockJsoupConnect("https://www.audible.co.jp/pd/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "co.jp"));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.co.jp/pd/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_removesExtraWhitespace() throws Exception {
        mockJsoupConnect("https://www.audible.com/pd/EXAMPLESKU", "<html />");

        Book book = getBook("  EXAMPLESKU  ");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.com/pd/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_removesExtraCharacters() throws Exception {
        mockJsoupConnect("https://www.audible.com/pd/EXAMPLESKU", "<html />");

        Book book = getBook("@EXAMPLESKU!!");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.com/pd/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_ignoresInvalidBookASIN() throws Exception {
        mockASINSearch("Example");
        mockJsoupConnect("https://www.audible.com/pd/SEARCHASIN", "<html />");

        // Not 10 characters, alpha-numeric.
        Book book = getBook("bad asin");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.com/pd/SEARCHASIN"));
    }

    @Test
    public void fetchTopMetadata_ignoresNonSearchResults_withResults() throws Exception {
        mockExampleProductPage();

        mockJsoupConnect(
                "https://www.audible.com/search?keywords=Example",
                """
                <html><body>
                <a href="https://www.audible.com/pd/something/AAAAAAAAAA">Example</a>
                <div data-widget="productList">
                <a href="https://www.audible.com/pd/something/B0FQSDKVQ8">Example</a>
                </div>
                </body></html>
                """
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        BookMetadata bookMetadata = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertNotNull(bookMetadata);
        assertEquals("B0FQSDKVQ8", bookMetadata.getAsin());
        assertEquals("PLC Programming for Industrial Automation", bookMetadata.getTitle());
    }

    @Test
    public void fetchTopMetadata_ignoresNonSearchResults_noResults() throws Exception {
        mockExampleProductPage();

        mockJsoupConnect(
                "https://www.audible.com/search?keywords=Example",
                """
                <html><body>
                <a href="https://www.audible.com/pd/something/B0FQSDKVQ8">Example</a>
                <div data-widget="productList">
                </div>
                </body></html>
                """
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        BookMetadata bookMetadata = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertNull(bookMetadata);
    }

    @Test
    public void fetchTopMetadata_ignoresMissingBookASIN() throws Exception {
        mockASINSearch("Example");
        mockJsoupConnect("https://www.audible.com/pd/SEARCHASIN", "<html />");

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.audible.com/pd/SEARCHASIN"));
    }
}
