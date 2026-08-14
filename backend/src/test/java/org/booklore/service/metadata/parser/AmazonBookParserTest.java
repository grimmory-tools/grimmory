package org.booklore.service.metadata.parser;


import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AmazonBookParserTest {
    @Mock private AppSettingService mockAppSettingService;

    @InjectMocks
    private AmazonBookParser amazonBookParser;

    private MockedStatic<Jsoup> mockJsoup;

    private String readFixture(String fixtureName) throws IOException {
        String filename = "amazon/" + fixtureName + ".fixture";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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

    private AppSettings getAppSettingsWithoutMetadataProviderSettings() {
        MetadataPublicReviewsSettings publicReviewsSettings = MetadataPublicReviewsSettings.builder()
                .providers(Collections.emptySet())
                .build();

        return AppSettings
                .builder()
                .metadataPublicReviewsSettings(publicReviewsSettings)
                .build();
    }

    private AppSettings getAppSettingsWithoutAmazonSettings() {
        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();

        MetadataPublicReviewsSettings publicReviewsSettings = MetadataPublicReviewsSettings.builder()
                .providers(Collections.emptySet())
                .build();

        return AppSettings
                .builder()
                .metadataPublicReviewsSettings(publicReviewsSettings)
                .metadataProviderSettings(metadataProviderSettings)
                .build();
    }

    private AppSettings getAppSettingsWithoutPublicReviewsSettings() {
        MetadataProviderSettings.Amazon amazonSettings = new MetadataProviderSettings.Amazon();
        amazonSettings.setEnabled(true);
        amazonSettings.setDomain("com");

        MetadataProviderSettings metadataProviderSettings = new MetadataProviderSettings();
        metadataProviderSettings.setAmazon(amazonSettings);

        return AppSettings
                .builder()
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
    public void fetchTopMetadata_parsesBasic_USEbook() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/B007978P18", readFixture("ebook-us.html"));

        Book book = getBook("B007978P18");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/B007978P18"));

        assertThat(result.getAsin()).isEqualTo("B007978P18");
        assertThat(result.getTitle()).isEqualTo("The Return Of The King");
    }

    @Test
    public void fetchTopMetadata_parsesBasic_DEPaperback() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/3608989420", readFixture("book-de.html"));

        Book book = getBook("3608989420");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/3608989420"));

        assertThat(result.getAsin()).isEqualTo("3608989420");
        assertThat(result.getTitle()).isEqualTo("Der Herr der Ringe. Bd. 3 - Die Rückkehr des Königs (Der Herr der Ringe. Ausgabe in neuer Übersetzung und Rechtschreibung, Bd. 3)");
    }

    @Test
    public void fetchTopMetadata_parsesBasic_DEEbook() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/B01BLSRIX6", readFixture("ebook-de.html"));

        Book book = getBook("B01BLSRIX6");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/B01BLSRIX6"));

        assertThat(result.getAsin()).isEqualTo("B01BLSRIX6");
        assertThat(result.getTitle()).isEqualTo("EchtzeiT - Wer die Wahrheit quält");
    }

    @Test
    public void fetchTopMetadata_parsesRatings_USEbook() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/B007978P18", readFixture("ebook-us.html"));

        Book book = getBook("B007978P18");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/B007978P18"));

        assertThat(result.getAmazonRating()).isNotNull();
        assertThat(result.getAmazonRating()).isEqualTo(4.9);
        assertThat(result.getAmazonReviewCount()).isNotNull();
        assertThat(result.getAmazonReviewCount()).isEqualTo(14906);
    }

    @Test
    public void fetchTopMetadata_parsesRatings_DEPaperback() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/3608989420", readFixture("book-de.html"));

        Book book = getBook("3608989420");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/3608989420"));

        assertThat(result.getAmazonRating()).isNotNull();
        assertThat(result.getAmazonRating()).isEqualTo(4.7);
        assertThat(result.getAmazonReviewCount()).isNotNull();
        assertThat(result.getAmazonReviewCount()).isEqualTo(463);
    }

    @Test
    public void fetchTopMetadata_parsesRatings_DEEbook() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/B01BLSRIX6", readFixture("ebook-de.html"));

        Book book = getBook("B01BLSRIX6");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/B01BLSRIX6"));

        assertThat(result.getAmazonRating()).isNotNull();
        assertThat(result.getAmazonRating()).isEqualTo(4.5);
        assertThat(result.getAmazonReviewCount()).isNotNull();
        assertThat(result.getAmazonReviewCount()).isEqualTo(2423);
    }

    @Test
    public void fetchTopMetadata_parsesPageCount_USEbook() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/B007978P18", readFixture("ebook-us.html"));

        Book book = getBook("B007978P18");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/B007978P18"));

        assertThat(result.getPageCount()).isNotNull();
        assertThat(result.getPageCount()).isEqualTo(432);
    }

    @Test
    public void fetchTopMetadata_parsesPageCount_DEPaperback() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/3608989420", readFixture("book-de.html"));

        Book book = getBook("3608989420");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/3608989420"));

        assertThat(result.getPageCount()).isNotNull();
        assertThat(result.getPageCount()).isEqualTo(448);
    }

    @Test
    public void fetchTopMetadata_parsesPageCount_DEEbook() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/B01BLSRIX6", readFixture("ebook-de.html"));

        Book book = getBook("B01BLSRIX6");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/B01BLSRIX6"));

        assertThat(result.getPageCount()).isNotNull();
        assertThat(result.getPageCount()).isEqualTo(414);
    }

    @Test
    public void fetchTopMetadata_parsesSeries_USEbook() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/B007978P18", readFixture("ebook-us.html"));

        Book book = getBook("B007978P18");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/B007978P18"));

        assertThat(result.getSeriesName()).isEqualTo("Lord of the Rings");
        assertThat(result.getSeriesNumber()).isNotNull();
        assertThat(result.getSeriesNumber()).isEqualTo(3);
        assertThat(result.getSeriesTotal()).isNotNull();
        assertThat(result.getSeriesTotal()).isEqualTo(3);
    }

    @Test
    public void fetchTopMetadata_parsesSeries_DEPaperback() throws Exception {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "de"));

        mockJsoupConnect("https://www.amazon.de/dp/3608989439", readFixture("book-de.html"));

        Book book = getBook("3608989439");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.de/dp/3608989439"));

        assertThat(result.getSeriesName()).isEqualTo("Der Herr der Ringe. Ausgabe in neuer Übersetzung und Rechtschreibung");
        assertThat(result.getSeriesNumber()).isNotNull();
        assertThat(result.getSeriesNumber()).isEqualTo(3);
        assertThat(result.getSeriesTotal()).isNotNull();
        assertThat(result.getSeriesTotal()).isEqualTo(3);
    }

    @Test
    public void fetchTopMetadata_parsesSeries_DEEbook() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/B01BLSRIX6", readFixture("ebook-de.html"));

        Book book = getBook("B01BLSRIX6");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/B01BLSRIX6"));

        assertThat(result.getSeriesName()).isEqualTo("EchtzeiT");
        assertThat(result.getSeriesNumber()).isNotNull();
        assertThat(result.getSeriesNumber()).isEqualTo(2);
        assertThat(result.getSeriesTotal()).isNotNull();
        assertThat(result.getSeriesTotal()).isEqualTo(3);
    }

    @Test
    public void fetchTopMetadata_parsesSeries_Empty() throws Exception {
        mockJsoupConnect(
                "https://www.amazon.com/dp/EXAMPLESKU",
                "<html><body></body><html>"
        );

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        var result = amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));

        assertThat(result.getSeriesName()).isNull();
        assertThat(result.getSeriesNumber()).isNull();
        assertThat(result.getSeriesTotal()).isNull();
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
    public void fetchTopMetadata_failsWhenDomainIsUnsupported() {
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings("com@evil.example"));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        assertThatThrownBy(() -> amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest))
                .isInstanceOfSatisfying(APIException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(ApiError.INVALID_INPUT.getStatus());
                    assertThat(exception.getMessage()).isEqualTo("Unsupported Amazon domain: com@evil.example");
                });

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com@evil.example/dp/EXAMPLESKU"), never());
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenDomainIsNull() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings(null));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenDomainIsEmpty() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings(""));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenDomainIsBlank() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings("   "));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenAppSettingsAreMissing() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(null);

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenMetadataProviderSettingsAreMissing() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettingsWithoutMetadataProviderSettings());

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fallsBackToDefaultDomainWhenAmazonSettingsAreMissing() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettingsWithoutAmazonSettings());

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
    }

    @Test
    public void fetchTopMetadata_fetchesMetadataWhenPublicReviewsSettingsAreMissing() throws Exception {
        mockJsoupConnect("https://www.amazon.com/dp/EXAMPLESKU", "<html />");
        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettingsWithoutPublicReviewsSettings());

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        amazonBookParser.fetchTopMetadata(book, fetchMetadataRequest);

        mockJsoup.verify(() -> Jsoup.connect("https://www.amazon.com/dp/EXAMPLESKU"));
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
