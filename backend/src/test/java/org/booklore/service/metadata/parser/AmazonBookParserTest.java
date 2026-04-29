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
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AmazonBookParserTest {
    @Mock private AppSettingService mockAppSettingService;

    @InjectMocks
    private AmazonBookParser amazonBookParser;

    private MockedStatic<Jsoup> mockJsoup;

    private AppSettings getAppSettings(String domain) {
        MetadataProviderSettings.Amazon amazonSettings = new MetadataProviderSettings.Amazon();
        amazonSettings.setEnabled(true);
        amazonSettings.setDomain(domain);

        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();
        metadataProviderSettings.setAmazon(amazonSettings);

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
        when(mockConnection.execute()).thenReturn(mockResponse);

        when(mockResponse.parse()).thenReturn(document);

        return mockConnection;
    }

    private void mockJsoupConnect(String url, String html) throws Exception {
        Document document = Parser.parse(html, "");
        Connection connection = getConnection(document);

        mockJsoup.when(() -> Jsoup.connect(url))
                .thenReturn(connection);
    }

    private void mockAmazonIDSearch(String keyword) throws Exception {
        mockJsoupConnect(
                "https://www.amazon.com/s?k=" + keyword,
                """
                <html><body>
                <span data-component-type="s-search-results">
                <div div role="listitem" data-index>
                    <div data-cy="title-recipe">Example</div>
                    <a href="https://www.amazon.com/dp/SEARCHASIN">Paperback</a>
                </div>
                </span>
                </body></html>
                """
        );

    }

    @BeforeEach
    public void setup() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "com"));

        mockJsoup = mockStatic(Jsoup.class);
    }

    @AfterEach
    void tearDown() {
        mockJsoup.close();
    }

    @Test
    public void fetchTopMetadata_usesAsinFromBookWhenAvailable() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_useDomain() throws Exception {
        mockJsoupConnect("https://www.amazon.co.jp/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "co.jp"));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.co.jp/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_removesExtraWhitespace() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");

        Book book = getBook("  EXAMPLESKU  ");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_removesExtraCharacters() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");

        Book book = getBook("@EXAMPLESKU!!");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_ignoresInvalidBookASINUsesLink() throws Exception {
        mockAmazonIDSearch("Example");
        mockJsoupConnect("https://www.amazon.com/dp/SEARCHASIN", "<html />");

        // Not 10 characters, alpha-numeric.
        Book book = getBook("bad asin");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/SEARCHASIN"));
    }

    @Test
    public void fetchTopMetadata_ignoresMissingBookASINUsesLink() throws Exception {
        mockAmazonIDSearch("Example");
        mockJsoupConnect("https://www.amazon.com/dp/SEARCHASIN", "<html />");

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/SEARCHASIN"));
    }

    @Test
    public void fetchTopMetadata_cleansLineBreaks() throws Exception {
        String fakePageHtml = """
        <html><body>
        <div class="product-description">
        <br /><br   ><br><div>Hello</div><br /><br /><br><div>World</div><br /><br   ><br>
        </div>
        </body></html>
        """;

        mockAmazonIDSearch("Example");
        mockJsoupConnect(
                "https://www.amazon.com/dp/SEARCHASIN", fakePageHtml);

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        mockJsoup.when(() -> Jsoup.parse(anyString())).thenCallRealMethod();

        BookMetadata metadata = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        String actual = metadata.getDescription().replace("\n", "");
        assertEquals("<div>Hello</div><br><br><div>World</div>", actual);
    }
}
