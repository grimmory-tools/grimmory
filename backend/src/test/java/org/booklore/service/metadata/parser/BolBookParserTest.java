package org.booklore.service.metadata.parser;

import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

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
        parser = new BolBookParser(new ObjectMapper());
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
        assertThat(metadata.getIsbn13()).isEqualTo("9789076682266");
        assertThat(metadata.getIsbn10()).isNull();
        assertThat(metadata.getRating()).isEqualTo(4.3);
        assertThat(metadata.getLanguage()).isEqualTo("nl");
        assertThat(metadata.getCategories()).contains("Thrillers & Spanning", "Literaire thrillers");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2004, 7, 24));
        assertThat(metadata.getPageCount()).isEqualTo(425);
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

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("bol", fixtureName).toString();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assertThat(is).as("fixture %s not found", filename).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}