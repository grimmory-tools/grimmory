package org.booklore.opf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpfMetadataExtractorTest {

    private final OpfMetadataExtractor extractor = new OpfMetadataExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extractsAllowedFieldsAndIgnoresForbiddenFields() throws Exception {
        Path opf = tempDir.resolve("metadata.opf");
        Files.writeString(opf, """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                  <metadata>
                    <dc:title>Sidecar Title</dc:title>
                    <dc:creator>Author A</dc:creator>
                    <dc:publisher>Publisher A</dc:publisher>
                    <dc:date>2026-06-05</dc:date>
                    <dc:description>Description A</dc:description>
                    <dc:language>en</dc:language>
                    <dc:subject>Fantasy</dc:subject>
                    <dc:identifier opf:scheme="ISBN">9781234567890</dc:identifier>
                    <meta name="calibre:series" content="Series A"/>
                    <meta name="calibre:series_index" content="2"/>
                    <meta name="rating" content="5"/>
                  </metadata>
                </package>
                """);

        var metadata = extractor.extract(opf).orElseThrow();

        assertThat(metadata.getTitle()).isEqualTo("Sidecar Title");
        assertThat(metadata.getAuthors()).containsExactly("Author A");
        assertThat(metadata.getPublisher()).isEqualTo("Publisher A");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(metadata.getDescription()).isEqualTo("Description A");
        assertThat(metadata.getLanguage()).isEqualTo("en");
        assertThat(metadata.getCategories()).isEqualTo(Set.of("Fantasy"));
        assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
        assertThat(metadata.getSeriesName()).isEqualTo("Series A");
        assertThat(metadata.getSeriesNumber()).isEqualTo(2.0f);
        assertThat(metadata.getRating()).isNull();
    }

    @Test
    void malformedOpfDoesNotFailScan() throws Exception {
        Path opf = tempDir.resolve("metadata.opf");
        Files.writeString(opf, "<package><metadata>");

        assertThat(extractor.extract(opf)).isEmpty();
    }
}
