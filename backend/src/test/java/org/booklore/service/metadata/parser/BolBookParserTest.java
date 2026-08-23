package org.booklore.service.metadata.parser;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses a real bol.com product page fixture (schema.org Book JSON-LD) and
 * checks the fields that feed Grimmory's metadata pipeline.
 */
class BolBookParserTest {

    private BolBookParser parser;

    @BeforeEach
    void setUp() {
        parser = new BolBookParser(JsonMapper.shared());
    }

    @Test
    void parsesProductPageJsonLd() throws IOException {
        String html = readFixture("example-product.html.fixture");

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getProvider()).isEqualTo(org.booklore.model.enums.MetadataProvider.Bol);
        assertThat(metadata.getTitle()).isEqualTo("O'Loughlin 1 - De verdenking");
        assertThat(metadata.getAuthors()).containsExactly("Michael Robotham");
        assertThat(metadata.getPublisher()).isEqualTo("Cargo");
        assertThat(metadata.getIsbn13()).isEqualTo("9789023449249");
        assertThat(metadata.getIsbn10()).isNull();
        assertThat(metadata.getRating()).isEqualTo(4.3);
        assertThat(metadata.getLanguage()).isEqualTo("nl");
        assertThat(metadata.getCategories()).contains("Thrillers & Spanning", "Literaire thrillers");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2010, 9, 14));
        assertThat(metadata.getPageCount()).isNull();
        assertThat(metadata.getThumbnailUrl()).startsWith("https://media.s-bol.com/");
        assertThat(metadata.getDescription()).isNotBlank();
    }

    @Test
    void ignoresNonBookJsonLdBlocks() throws IOException {
        String html = """
                <html><body>
                <script type="application/ld+json">{"@type":"BreadcrumbList","itemListElement":[]}</script>
                </body></html>""";

        assertThat(parser.parseProductPage(html)).isNull();
    }

    @Test
    void handlesMissingOptionalFields() {
        String html = """
                <html><body>
                <script type="application/ld+json">{"@type":"Book","name":"Minimal"}</script>
                </body></html>""";

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("Minimal");
        assertThat(metadata.getAuthors()).isEmpty();
        assertThat(metadata.getIsbn13()).isNull();
        assertThat(metadata.getRating()).isNull();
    }

    @Test
    void returnsNullForEmptyPage() {
        assertThat(parser.parseProductPage("")).isNull();
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(parser.parseProductPage(null)).isNull();
    }

    @Test
    void usesCleanedIsbnAsQuery() {
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("978-90-243-4924-9")
                .title("Ignored")
                .build();

        assertThat(parser.buildQuery(request, Book.builder().build())).isEqualTo("9789024349249");
    }

    @Test
    void fallsBackToTitleWhenIsbnIsUnusable() {
        FetchMetadataRequest request = FetchMetadataRequest.builder()
                .isbn("not-an-isbn")
                .title("De verdenking")
                .build();

        assertThat(parser.buildQuery(request, Book.builder().build())).isEqualTo("De verdenking");
    }

    @Test
    void handlesObjectValuedWorkExample() {
        String html = """
                <html><body>
                <script type="application/ld+json">{"@type":"Book","name":"De verdenking","url":"https://www.bol.com/nl/nl/p/de-verdenking/1001004001998403/","workExample":{"@type":"Book","name":"De Verdenking","url":"https://www.bol.com/nl/nl/p/de-verdenking/1001004001998403/","isbn":"9789076682266","datePublished":"2004-07-24","numberOfPages":"425"}}</script>
                </body></html>""";

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("De verdenking");
        assertThat(metadata.getIsbn13()).isEqualTo("9789076682266");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2004, 7, 24));
        assertThat(metadata.getPageCount()).isEqualTo(425);
    }

    @Test
    void handlesTopLevelJsonLdArray() {
        String html = """
                <html><body>
                <script type="application/ld+json">[{"@type":"BreadcrumbList","itemListElement":[]},{"@type":"Book","name":"De verdenking","gtin13":"9789076682266","datePublished":"2004-07-24","workExample":{"@type":"Book","isbn":"9789076682266","numberOfPages":"425"}}]</script>
                </body></html>""";

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("De verdenking");
        assertThat(metadata.getIsbn13()).isEqualTo("9789076682266");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2004, 7, 24));
        assertThat(metadata.getPageCount()).isEqualTo(425);
    }

    @Test
    void handlesGraphContainer() {
        String html = """
                <html><body>
                <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"BreadcrumbList","itemListElement":[]},{"@type":"Book","name":"De verdenking","gtin13":"9789076682266","datePublished":"2004-07-24","workExample":{"@type":"Book","isbn":"9789076682266","numberOfPages":"425"}}]}</script>
                </body></html>""";

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("De verdenking");
        assertThat(metadata.getIsbn13()).isEqualTo("9789076682266");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2004, 7, 24));
        assertThat(metadata.getPageCount()).isEqualTo(425);
    }

    @Test
    void handlesObjectValuedGraphContainer() {
        String html = """
                <html><body>
                <script type="application/ld+json">{"@context":"https://schema.org","@graph":{"@type":"Book","name":"De verdenking","gtin13":"9789076682266","datePublished":"2004-07-24","workExample":{"@type":"Book","isbn":"9789076682266","numberOfPages":"425"}}}</script>
                </body></html>""";

        BookMetadata metadata = parser.parseProductPage(html);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("De verdenking");
        assertThat(metadata.getIsbn13()).isEqualTo("9789076682266");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2004, 7, 24));
        assertThat(metadata.getPageCount()).isEqualTo(425);
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("bol", fixtureName).toString();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assertThat(is).as("fixture %s not found", filename).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}