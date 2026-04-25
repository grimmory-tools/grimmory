package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpfMetadataExtractorTest {

    private OpfMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new OpfMetadataExtractor();
    }

    @Test
    void extractMetadata_readsCalibreOpfFieldsWithUnicode() throws Exception {
        Path opf = writeOpf("book.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                         xmlns:dc="http://purl.org/dc/elements/1.1/"
                         xmlns:opf="http://www.idpf.org/2007/opf"
                         version="2.0">
                  <metadata>
                    <dc:title>โรงเรียนปีศาจ DxD เล่ม 1</dc:title>
                    <dc:creator>Ichiei Ishibumi</dc:creator>
                    <dc:creator>Miyama-Zero</dc:creator>
                    <dc:publisher>Kadokawa</dc:publisher>
                    <dc:date>2024-05</dc:date>
                    <dc:description>นิยายแปลไทย ทดสอบยูนิโค้ด</dc:description>
                    <dc:language>th</dc:language>
                    <dc:subject>Light Novel</dc:subject>
                    <dc:subject>Fantasy</dc:subject>
                    <dc:identifier opf:scheme="ISBN">978-616-123-456-7</dc:identifier>
                    <meta name="calibre:series" content="High School DxD"/>
                    <meta name="calibre:series_index" content="1"/>
                  </metadata>
                </package>
                """);

        BookMetadata metadata = extractor.extractMetadata(opf.toFile());

        assertThat(metadata.getTitle()).isEqualTo("โรงเรียนปีศาจ DxD เล่ม 1");
        assertThat(metadata.getAuthors()).containsExactly("Ichiei Ishibumi", "Miyama-Zero");
        assertThat(metadata.getPublisher()).isEqualTo("Kadokawa");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(metadata.getDescription()).isEqualTo("นิยายแปลไทย ทดสอบยูนิโค้ด");
        assertThat(metadata.getLanguage()).isEqualTo("th");
        assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Light Novel", "Fantasy");
        assertThat(metadata.getIsbn13()).isEqualTo("9786161234567");
        assertThat(metadata.getSeriesName()).isEqualTo("High School DxD");
        assertThat(metadata.getSeriesNumber()).isEqualTo(1.0f);
    }

    @Test
    void extractMetadata_prefersSpecificIsbnSchemeAndParsesTimestampDate() throws Exception {
        Path opf = writeOpf("book.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf"
                         xmlns:dc="http://purl.org/dc/elements/1.1/"
                         xmlns:opf="http://www.idpf.org/2007/opf"
                         version="2.0">
                  <metadata>
                    <dc:title>Example</dc:title>
                    <dc:date>2024-05-17T21:14:00+07:00</dc:date>
                    <dc:identifier opf:scheme="ISBN">0-123-45678-9</dc:identifier>
                    <dc:identifier opf:scheme="ISBN13">9780001112223</dc:identifier>
                  </metadata>
                </package>
                """);

        BookMetadata metadata = extractor.extractMetadata(opf.toFile());

        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2024, 5, 17));
        assertThat(metadata.getIsbn10()).isEqualTo("0123456789");
        assertThat(metadata.getIsbn13()).isEqualTo("9780001112223");
    }

    @Test
    void extractMetadata_invalidXmlThrowsHelpfulError() throws Exception {
        Path opf = writeOpf("broken.opf", "<package><metadata><dc:title>Broken");

        assertThatThrownBy(() -> extractor.extractMetadata(opf.toFile()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse OPF metadata");
    }

    private Path writeOpf(String fileName, String contents) throws Exception {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, contents);
        return path;
    }
}
