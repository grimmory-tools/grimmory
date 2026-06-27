package org.booklore.service.metadata.parser;


import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataProviderSettings;
import org.booklore.model.dto.settings.MetadataPublicReviewsSettings;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AudibleParserTest {
    @Mock private AppSettingService mockAppSettingService;

    @Mock private HttpClient mockHttpClient;

    private AudibleParser audibleParser;

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

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> getMockResponse(int statusCode, String response) {
        HttpResponse<InputStream> httpResponse = (HttpResponse<InputStream>) mock(HttpResponse.class);

        when(httpResponse.statusCode()).thenReturn(statusCode);

        if (response != null) {
            when(httpResponse.body()).thenAnswer(
                    (_) -> new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))
            );
        }

        return httpResponse;
    }

    private void mockHttpClientResponse(String urlPrefix, int statusCode, String response) throws Exception {
        when(
                mockHttpClient.<InputStream>send(
                        argThat(r -> r != null && r.uri().toString().startsWith(urlPrefix)),
                        any()
                )
        ).thenAnswer((_) -> getMockResponse(statusCode, response));
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = "audible/" + fixtureName + ".fixture";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @BeforeEach
    public void setup() throws Exception {
        audibleParser = new AudibleParser(
                mockAppSettingService,
                new ObjectMapper(),
                mockHttpClient
        );

        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings("com"));
    }

    @Test
    public void fetchTopMetadata_usesAsinFromBookWhenAvailable() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("example-asin-lookup.json")
        );

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_useDomain() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.co.jp/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("example-asin-lookup.json")
        );

        when(mockAppSettingService.getAppSettings()).thenReturn(getAppSettings( "co.jp"));

        Book book = getBook("EXAMPLESKU");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_removesExtraWhitespace() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("example-asin-lookup.json")
        );

        Book book = getBook("  EXAMPLESKU  ");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_removesExtraCharactersFromBook() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("example-asin-lookup.json")
        );

        Book book = getBook("@EXAMPLESKU!!");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_removesExtraCharactersFromRequest() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("example-asin-lookup.json")
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder()
                .asin("@EXAMPLESKU!! ")
                .build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_ignoresInvalidBookASIN() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products?",
                200,
                readFixture("example-search.json")
        );

        // Not 10 characters, alpha-numeric.
        Book book = getBook("bad asin");
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Example").build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNotNull();
    }

    @Test
    public void fetchTopMetadata_ignoresMissingBookASIN() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/EXAMPLESKU",
                200,
                readFixture("missing-asin-lookup.json")
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().asin("EXAMPLESKU").build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual).isNull();
    }

    @Test
    public void fetchTopMetadata_parsesFields() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products/1705047572",
                200,
                readFixture("example-asin-lookup.json")
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().asin("1705047572").build();

        BookMetadata actual = audibleParser.fetchTopMetadata(book, fetchMetadataRequest);

        assertThat(actual.getAsin()).isEqualTo("1705047572");
        assertThat(actual.getTitle()).isEqualTo("The Fellowship of the Ring");
        assertThat(actual.getSubtitle()).isEqualTo("Lord of the Rings, Book 1");
        assertThat(actual.getAuthors()).hasSize(1);
        assertThat(actual.getAuthors().getFirst()).isEqualTo("J. R. R. Tolkien");
        assertThat(actual.getNarrator()).isEqualTo("Andy Serkis");
        assertThat(actual.getSeriesName()).isEqualTo("The Lord of the Rings");
        assertThat(actual.getSeriesNumber()).isEqualTo(1);
        assertThat(actual.getDescription()).startsWith("This brand-new unabridged audiobook of The Fellowship of the Ring,");
        assertThat(actual.getDescription()).hasSize(607);
        assertThat(actual.getAbridged()).isFalse();
        assertThat(actual.getThumbnailUrl()).isEqualTo("https://m.media-amazon.com/images/I/81Yraj9IJDL._SL1000_.jpg");
        assertThat(actual.getCategories()).isEqualTo(Set.of("Classics", "Literature & Fiction"));
    }

    @Test
    public void fetchMetadata_ignoresPodcasts() throws Exception {
        mockHttpClientResponse(
                "https://api.audible.com/1.0/catalog/products",
                200,
                readFixture("example-search-with-podcasts.json")
        );

        Book book = getBook(null);
        FetchMetadataRequest fetchMetadataRequest = FetchMetadataRequest.builder().title("Lore").build();

        List<BookMetadata> actual = audibleParser.fetchMetadata(book, fetchMetadataRequest);

        assertThat(actual).hasSize(13);
        assertThat(actual.stream().map(BookMetadata::getAsin).anyMatch("B08JJSB33N"::equalsIgnoreCase)).isFalse();
    }
}
