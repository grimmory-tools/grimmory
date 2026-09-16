package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PerrypediaParserTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private HttpClient httpClient;

    @InjectMocks
    private PerrypediaParser parser;

    private String readFixture(String name) throws IOException {
        String filename = "perrypedia/" + name + ".fixture";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A minimal rendered-infobox HTML fragment with just the "Zyklus" row this parser reads. */
    private String zyklusHtml(String cycleName) {
        return infoboxHtml(cycleName, "");
    }

    /** Same infobox fragment as {@link #zyklusHtml}, plus arbitrary extra HTML (e.g. cover {@code <img>} tags). */
    private String infoboxHtml(String cycleName, String extraHtml) {
        return "<div class=\"mw-parser-output\"><table>"
                + "<tr><td>Serie:</td><td>Perry Rhodan</td></tr>"
                + "<tr><td>Zyklus:</td><td><a href=\"/wiki/X\">" + cycleName + "</a></td></tr>"
                + "</table>" + extraHtml + "</div>";
    }

    /** Real infobox cover <img> markup for "PR3000.jpg", captured live 2026-08-29 — see TASK-metadata-perrypedia.md §0. */
    private static final String PR3000_COVER_IMG =
            "<img alt=\"PR3000.jpg\" src=\"/mediawiki/images/thumb/1/12/PR3000.jpg/360px-PR3000.jpg\" "
                    + "srcset=\"/mediawiki/images/thumb/1/12/PR3000.jpg/540px-PR3000.jpg 1.5x, "
                    + "/mediawiki/images/thumb/1/12/PR3000.jpg/720px-PR3000.jpg 2x\" />";

    /** Real infobox disambiguation-marker <img>, precedes the cover on disambiguation-flagged pages. */
    private static final String DISAMBIGUATION_ICON_IMG =
            "<img alt=\"Logo_Begriffsklärung.png\" src=\"/mediawiki/images/thumb/2/2f/Logo_Begriffskl%C3%A4rung.png/25px-Logo_Begriffskl%C3%A4rung.png\" />";

    /** Builds a formatversion=2 action=parse response wrapping the given wikitext and rendered HTML. */
    private String buildParseResponse(String redirectFrom, String resolvedTitle, String wikitext, String html) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode parse = root.putObject("parse");
        parse.put("title", resolvedTitle);
        if (redirectFrom != null) {
            parse.putArray("redirects").addObject().put("from", redirectFrom).put("to", resolvedTitle);
        }
        parse.put("wikitext", wikitext);
        parse.put("text", html);
        return root.toString();
    }

    private String buildSearchResponse(String title) {
        ObjectNode root = objectMapper.createObjectNode();
        var search = root.putObject("query").putArray("search");
        search.addObject().put("title", title);
        return root.toString();
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> getResponse(int statusCode, String payload) {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(statusCode);
        when(mockResponse.body()).thenReturn(payload);
        return mockResponse;
    }

    private void mockResponse(String uriContains, int statusCode, String payload) throws IOException, InterruptedException {
        HttpResponse<String> response = getResponse(statusCode, payload);
        when(
                httpClient.<String>send(
                        argThat(arg -> arg != null && arg.uri().toString().contains(uriContains)),
                        any()
                )
        ).thenReturn(response);
    }

    private void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fetchTopMetadata_EmptySearchTerm_ReturnsNull() {
        setUp();

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().build();

        assertThat(parser.fetchTopMetadata(book, request)).isNull();
    }

    @Test
    void fetchMetadata_EmptySearchTerm_ReturnsEmptyList() {
        setUp();

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().build();

        assertThat(parser.fetchMetadata(book, request)).isEmpty();
    }

    @Test
    void fetchTopMetadata_ClassicSeries_ResolvesViaSourceId() throws Exception {
        setUp();
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"), zyklusHtml("Mythos")));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PR 3000 - Mythos Erde").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("PR3000");
        assertThat(metadata.getTitle()).isEqualTo("Mythos Erde");
        assertThat(metadata.getSubtitle()).isEqualTo("Die Zeit verändert alles");
        assertThat(metadata.getAuthors()).containsExactly("Christian Montillon", "Wim Vandemaan");
        assertThat(metadata.getSeriesName()).isEqualTo("Mythos");
        assertThat(metadata.getSeriesNumber()).isEqualTo(3000.0f);
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2019, 2, 15));

        List<BookMetadata> results = parser.fetchMetadata(book, request);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getPerrypediaId()).isEqualTo("PR3000");
    }

    @Test
    void extractCoverUrl_PicksWidestSrcsetCandidateAndResolvesAbsoluteUrl() throws Exception {
        setUp();
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"),
                        infoboxHtml("Mythos", PR3000_COVER_IMG)));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PR 3000 - Mythos Erde").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        // 720px is the widest (2x) srcset candidate, not the bare 360px src.
        assertThat(metadata.getThumbnailUrl())
                .isEqualTo("https://www.perrypedia.de/mediawiki/images/thumb/1/12/PR3000.jpg/720px-PR3000.jpg");
    }

    @Test
    void extractCoverUrl_SkipsDisambiguationIconAndFindsCoverAfterIt() throws Exception {
        setUp();
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"),
                        infoboxHtml("Mythos", DISAMBIGUATION_ICON_IMG + PR3000_COVER_IMG)));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PR 3000 - Mythos Erde").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getThumbnailUrl())
                .isEqualTo("https://www.perrypedia.de/mediawiki/images/thumb/1/12/PR3000.jpg/720px-PR3000.jpg");
    }

    @Test
    void extractCoverUrl_NoImagePresent_ReturnsNullThumbnailUrl() throws Exception {
        setUp();
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"), zyklusHtml("Mythos")));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PR 3000 - Mythos Erde").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getThumbnailUrl()).isNull();
    }

    @Test
    void fetchTopMetadata_NeoSeries_ResolvesViaSourceId() throws Exception {
        setUp();
        mockResponse("Quelle:PRN389", 200,
                buildParseResponse("Quelle:PRN389", "Wenn Sterne bluten", readFixture("wenn_sterne_bluten"), zyklusHtml("Artefakte")));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PRN389 - Wenn Sterne bluten").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("PRN389");
        assertThat(metadata.getTitle()).isEqualTo("Wenn Sterne bluten");
        assertThat(metadata.getSeriesName()).isEqualTo("Artefakte");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void fetchTopMetadata_AtlanSeries_ResolvesViaSourceId() throws Exception {
        setUp();
        mockResponse("Quelle:A800", 200,
                buildParseResponse("Quelle:A800", "Die Zeitfestung", readFixture("die_zeitfestung"), zyklusHtml("Im Auftrag der Kosmokraten")));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("A800 - Die Zeitfestung").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("A800");
        assertThat(metadata.getTitle()).isEqualTo("Die Zeitfestung");
        assertThat(metadata.getSubtitle()).isEqualTo("Überraschung im Intern-Kosmos");
        assertThat(metadata.getSeriesName()).isEqualTo("Im Auftrag der Kosmokraten");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1987, 1, 27));
    }

    @Test
    void fetchDetailedMetadata_ClassicSeriesId_ReturnsMetadata() throws Exception {
        setUp();
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"), zyklusHtml("Mythos")));

        BookMetadata metadata = parser.fetchDetailedMetadata("PR3000");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("PR3000");
        assertThat(metadata.getTitle()).isEqualTo("Mythos Erde");
        assertThat(metadata.getSeriesName()).isEqualTo("Mythos");
    }

    @Test
    void fetchDetailedMetadata_NeoSeriesId_ReturnsMetadata() throws Exception {
        setUp();
        mockResponse("Quelle:PRN389", 200,
                buildParseResponse("Quelle:PRN389", "Wenn Sterne bluten", readFixture("wenn_sterne_bluten"), zyklusHtml("Artefakte")));

        BookMetadata metadata = parser.fetchDetailedMetadata("PRN389");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("PRN389");
        assertThat(metadata.getSeriesName()).isEqualTo("Artefakte");
    }

    @Test
    void fetchDetailedMetadata_AtlanSeriesId_ReturnsMetadata() throws Exception {
        setUp();
        mockResponse("Quelle:A800", 200,
                buildParseResponse("Quelle:A800", "Die Zeitfestung", readFixture("die_zeitfestung"), zyklusHtml("Im Auftrag der Kosmokraten")));

        BookMetadata metadata = parser.fetchDetailedMetadata("A800");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("A800");
        assertThat(metadata.getSeriesName()).isEqualTo("Im Auftrag der Kosmokraten");
    }

    @Test
    void fetchDetailedMetadata_LowercaseAndSeparatorVariant_StillResolves() throws Exception {
        setUp();
        mockResponse("Quelle:PRN389", 200,
                buildParseResponse("Quelle:PRN389", "Wenn Sterne bluten", readFixture("wenn_sterne_bluten"), zyklusHtml("Artefakte")));

        // extractSourceId tolerates lowercase prefixes and an optional separator, even though
        // key() itself only ever produces the canonical "PRN389" form stored in perrypediaId.
        BookMetadata metadata = parser.fetchDetailedMetadata("prn-389");

        assertThat(metadata).isNotNull();
        assertThat(metadata.getPerrypediaId()).isEqualTo("PRN389");
    }

    @Test
    void fetchDetailedMetadata_NullOrBlankId_ReturnsNullWithoutRequest() {
        setUp();

        assertThat(parser.fetchDetailedMetadata(null)).isNull();
        assertThat(parser.fetchDetailedMetadata("")).isNull();
        assertThat(parser.fetchDetailedMetadata("   ")).isNull();

        verifyNoInteractions(httpClient);
    }

    @Test
    void fetchDetailedMetadata_UnrecognizedFormat_ReturnsNullWithoutRequest() {
        setUp();

        assertThat(parser.fetchDetailedMetadata("not-a-source-id")).isNull();

        verifyNoInteractions(httpClient);
    }

    @Test
    void fetchTopMetadata_NoSourceId_FallsBackToTitleSearch() throws Exception {
        setUp();
        mockResponse("list=search", 200, buildSearchResponse("Wenn Sterne bluten"));
        mockResponse("page=Wenn", 200,
                buildParseResponse(null, "Wenn Sterne bluten", readFixture("wenn_sterne_bluten"), zyklusHtml("Artefakte")));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("Wenn Sterne bluten").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        // No source id could be matched from the search-resolved title itself, but the series
        // (and thus the perrypediaId prefix) is still recovered from the infobox template name.
        assertThat(metadata.getPerrypediaId()).isEqualTo("PRN389");
        assertThat(metadata.getSeriesName()).isEqualTo("Artefakte");
    }

    @Test
    void extractZyklus_MissingRow_ReturnsNullSeriesName() throws Exception {
        setUp();
        String htmlWithoutZyklus = "<div class=\"mw-parser-output\"><table><tr><td>Serie:</td><td>Perry Rhodan</td></tr></table></div>";
        mockResponse("Quelle:PR3000", 200,
                buildParseResponse("Quelle:PR3000", "Mythos Erde (Roman)", readFixture("mythos_erde"), htmlWithoutZyklus));

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("PR 3000 - Mythos Erde").build();

        BookMetadata metadata = parser.fetchTopMetadata(book, request);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getSeriesName()).isNull();
        // The rest of the metadata should still be present even without a Zyklus row.
        assertThat(metadata.getTitle()).isEqualTo("Mythos Erde");
    }

    /**
     * Regression test: "Wenn Schatten bluten" is not a real Perrypedia article — confirmed live
     * via the search API, {@code totalhits: 0} — so a book whose filename/title falls back to
     * this search text (e.g. a mistyped or garbled title) must not silently produce a wrong
     * result. This is the exact zero-hit response Perrypedia's search API returns for it.
     */
    @Test
    void fetchTopMetadata_SearchYieldsNoResults_ReturnsNull() throws Exception {
        setUp();
        mockResponse("list=search", 200, "{\"batchcomplete\":true,\"query\":{\"searchinfo\":{\"totalhits\":0},\"search\":[]}}");

        Book book = Book.builder().build();
        FetchMetadataRequest request = FetchMetadataRequest.builder().title("Wenn Schatten bluten").build();

        assertThat(parser.fetchTopMetadata(book, request)).isNull();
        assertThat(parser.fetchMetadata(book, request)).isEmpty();
    }
}
