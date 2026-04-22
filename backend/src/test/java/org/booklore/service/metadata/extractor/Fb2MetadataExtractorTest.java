package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class Fb2MetadataExtractorTest {

    private static final String NS = "http://www.gribuser.ru/xml/fictionbook/2.0";
    private static final String XLINK = "http://www.w3.org/1999/xlink";

    private Fb2MetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new Fb2MetadataExtractor();
    }

    private File writeFb2(String xmlBody) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="%s" xmlns:l="%s">
                %s
                </FictionBook>
                """.formatted(NS, XLINK, xmlBody);
        Path file = tempDir.resolve("test.fb2");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file.toFile();
    }

    private File writeFb2Gz(String xmlBody) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="%s" xmlns:l="%s">
                %s
                </FictionBook>
                """.formatted(NS, XLINK, xmlBody);
        Path file = tempDir.resolve("test.fb2.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(file))) {
            gzos.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        return file.toFile();
    }

    @Test
    void extractMetadata_titleAndAuthors() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <book-title>War and Peace</book-title>
                    <author>
                      <first-name>Leo</first-name>
                      <last-name>Tolstoy</last-name>
                    </author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("War and Peace");
        assertThat(metadata.getAuthors()).containsExactly("Leo Tolstoy");
    }

    @Test
    void extractMetadata_authorWithMiddleName() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author>
                      <first-name>Edgar</first-name>
                      <middle-name>Allan</middle-name>
                      <last-name>Poe</last-name>
                    </author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactly("Edgar Allan Poe");
    }

    @Test
    void extractMetadata_authorNicknameFallback() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author>
                      <nickname>voltaire</nickname>
                    </author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactly("voltaire");
    }

    @Test
    void extractMetadata_nicknameIgnoredWhenNamePartsPresent() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author>
                      <first-name>John</first-name>
                      <last-name>Doe</last-name>
                      <nickname>jdoe</nickname>
                    </author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactly("John Doe");
    }

    @Test
    void extractMetadata_multipleAuthors() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author><first-name>Alpha</first-name><last-name>One</last-name></author>
                    <author><first-name>Beta</first-name><last-name>Two</last-name></author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Alpha One", "Beta Two");
    }

    @Test
    void extractMetadata_genres() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <genre>sf_fantasy</genre>
                    <genre>adventure</genre>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getCategories()).containsExactlyInAnyOrder("sf_fantasy", "adventure");
    }

    @Test
    void extractMetadata_keywordsCommaSeparated() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <keywords>magic, dragons; wizards</keywords>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getCategories()).containsExactlyInAnyOrder("magic", "dragons", "wizards");
    }

    @Test
    void extractMetadata_keywordsAndGenresMerged() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <genre>fantasy</genre>
                    <keywords>magic, elves</keywords>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getCategories()).containsExactlyInAnyOrder("fantasy", "magic", "elves");
    }

    @Test
    void extractMetadata_isoDateFromTitleInfo() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <date value="2005-03-15">March 2005</date>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2005, 3, 15));
    }

    @Test
    void extractMetadata_dateValueAttributePreferredOverText() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <date value="2010-06-01">Some text 1999</date>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2010, 6, 1));
    }

    @Test
    void extractMetadata_yearOnlyDateFromText() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <date>1999</date>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1999, 1, 1));
    }

    @Test
    void extractMetadata_blankDateReturnsNull() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <date value="">  </date>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublishedDate()).isNull();
    }

    @Test
    void extractMetadata_language() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <lang>ru</lang>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getLanguage()).isEqualTo("ru");
    }

    @Test
    void extractMetadata_seriesWithNumber() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <sequence name="Discworld" number="5"/>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getSeriesName()).isEqualTo("Discworld");
        assertThat(metadata.getSeriesNumber()).isEqualTo(5.0f);
    }

    @Test
    void extractMetadata_seriesWithoutNumber() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <sequence name="Discworld"/>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getSeriesName()).isEqualTo("Discworld");
        assertThat(metadata.getSeriesNumber()).isNull();
    }

    @Test
    void extractMetadata_seriesInvalidNumberIgnored() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <sequence name="Series" number="abc"/>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getSeriesName()).isEqualTo("Series");
        assertThat(metadata.getSeriesNumber()).isNull();
    }

    @Test
    void extractMetadata_annotation() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <annotation>
                      <p>A great book about things.</p>
                      <p>Second paragraph.</p>
                    </annotation>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getDescription()).contains("A great book about things.");
        assertThat(metadata.getDescription()).contains("Second paragraph.");
    }

    @Test
    void extractMetadata_publishInfo() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info/>
                  <publish-info>
                    <publisher>Penguin Books</publisher>
                    <year>2001</year>
                  </publish-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublisher()).isEqualTo("Penguin Books");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2001, 1, 1));
    }

    @Test
    void extractMetadata_isbn13() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info/>
                  <publish-info>
                    <isbn>978-0-06-112008-4</isbn>
                  </publish-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getIsbn13()).isEqualTo("9780061120084");
    }

    @Test
    void extractMetadata_isbn10() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info/>
                  <publish-info>
                    <isbn>0-06-112008-X</isbn>
                  </publish-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getIsbn10()).isEqualTo("006112008X");
    }

    @Test
    void extractMetadata_isbnPatternMatch() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info/>
                  <publish-info>
                    <isbn>ISBN: 0-451-52493-4, another</isbn>
                  </publish-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getIsbn10()).isEqualTo("0451524934");
    }

    @Test
    void extractMetadata_gzipCompressedFile() throws IOException {
        File file = writeFb2Gz("""
                <description>
                  <title-info>
                    <book-title>Compressed Book</book-title>
                    <author><first-name>Gzip</first-name><last-name>Author</last-name></author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isEqualTo("Compressed Book");
        assertThat(metadata.getAuthors()).containsExactly("Gzip Author");
    }

    @Test
    void extractMetadata_emptyTitleInfo() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info/>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isNull();
        assertThat(metadata.getAuthors()).isEmpty();
    }

    @Test
    void extractMetadata_invalidXmlReturnsNull() throws IOException {
        Path file = tempDir.resolve("bad.fb2");
        Files.writeString(file, "this is not xml at all");

        BookMetadata metadata = extractor.extractMetadata(file.toFile());

        assertThat(metadata).isNull();
    }

    @Test
    void extractMetadata_fullDocument() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <genre>detective</genre>
                    <author><first-name>Arthur</first-name><middle-name>Conan</middle-name><last-name>Doyle</last-name></author>
                    <book-title>The Hound of the Baskervilles</book-title>
                    <annotation><p>A detective mystery.</p></annotation>
                    <keywords>mystery, detective</keywords>
                    <date value="1902-04-01"/>
                    <lang>en</lang>
                    <sequence name="Sherlock Holmes" number="5"/>
                  </title-info>
                  <publish-info>
                    <publisher>George Newnes</publisher>
                    <year>1902</year>
                    <isbn>978-0-14-043786-7</isbn>
                  </publish-info>
                  <document-info>
                    <id>abc-123</id>
                  </document-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getTitle()).isEqualTo("The Hound of the Baskervilles");
        assertThat(metadata.getAuthors()).containsExactly("Arthur Conan Doyle");
        assertThat(metadata.getCategories()).contains("detective", "mystery");
        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(1902, 1, 1));
        assertThat(metadata.getLanguage()).isEqualTo("en");
        assertThat(metadata.getSeriesName()).isEqualTo("Sherlock Holmes");
        assertThat(metadata.getSeriesNumber()).isEqualTo(5.0f);
        assertThat(metadata.getPublisher()).isEqualTo("George Newnes");
        assertThat(metadata.getIsbn13()).isEqualTo("9780140437867");
    }

    @Test
    void extractCover_binaryWithCoverId() throws IOException {
        byte[] imageData = {(byte) 0x89, 0x50, 0x4E, 0x47};
        String base64 = Base64.getEncoder().encodeToString(imageData);
        File file = writeFb2("""
                <description><title-info/></description>
                <binary id="cover.jpg" content-type="image/jpeg">%s</binary>
                """.formatted(base64));

        byte[] cover = extractor.extractCover(file);

        assertThat(cover).isEqualTo(imageData);
    }

    @Test
    void extractCover_fallbackToCoverpageReference() throws IOException {
        byte[] imageData = {0x01, 0x02, 0x03, 0x04};
        String base64 = Base64.getEncoder().encodeToString(imageData);
        File file = writeFb2("""
                <description>
                  <title-info>
                    <coverpage>
                      <image l:href="#img1"/>
                    </coverpage>
                  </title-info>
                </description>
                <binary id="img1" content-type="image/png">%s</binary>
                """.formatted(base64));

        byte[] cover = extractor.extractCover(file);

        assertThat(cover).isEqualTo(imageData);
    }

    @Test
    void extractCover_noBinaryReturnsNull() throws IOException {
        File file = writeFb2("""
                <description><title-info/></description>
                """);

        byte[] cover = extractor.extractCover(file);

        assertThat(cover).isNull();
    }

    @Test
    void extractCover_nonImageBinarySkipped() throws IOException {
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        File file = writeFb2("""
                <description><title-info/></description>
                <binary id="cover.dat" content-type="application/octet-stream">%s</binary>
                """.formatted(base64));

        byte[] cover = extractor.extractCover(file);

        assertThat(cover).isNull();
    }

    @Test
    void extractCover_gzipCompressedFile() throws IOException {
        byte[] imageData = {0x10, 0x20, 0x30};
        String base64 = Base64.getEncoder().encodeToString(imageData);
        File file = writeFb2Gz("""
                <description><title-info/></description>
                <binary id="cover.png" content-type="image/png">%s</binary>
                """.formatted(base64));

        byte[] cover = extractor.extractCover(file);

        assertThat(cover).isEqualTo(imageData);
    }

    @Test
    void extractCover_invalidFileReturnsNull() throws IOException {
        Path file = tempDir.resolve("bad.fb2");
        Files.writeString(file, "not xml");

        byte[] cover = extractor.extractCover(file.toFile());

        assertThat(cover).isNull();
    }

    @Test
    void extractMetadata_blankGenreSkipped() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <genre>  </genre>
                    <genre>valid</genre>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getCategories()).containsExactly("valid");
    }

    @Test
    void extractMetadata_blankAuthorSkipped() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author/>
                    <author><first-name>Valid</first-name></author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactly("Valid");
    }

    @Test
    void extractMetadata_publishInfoYearOverridesTitleInfoDate() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <date value="2005-03-15"/>
                  </title-info>
                  <publish-info>
                    <year>2010</year>
                  </publish-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2010, 1, 1));
    }

    @Test
    void extractMetadata_authorFirstNameOnly() throws IOException {
        File file = writeFb2("""
                <description>
                  <title-info>
                    <author><first-name>Madonna</first-name></author>
                  </title-info>
                </description>
                """);

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata.getAuthors()).containsExactly("Madonna");
    }

    @Test
    void extractMetadata_noDescriptionReturnsEmptyMetadata() throws IOException {
        File file = writeFb2("<body/>");

        BookMetadata metadata = extractor.extractMetadata(file);

        assertThat(metadata).isNotNull();
        assertThat(metadata.getTitle()).isNull();
        assertThat(metadata.getAuthors()).isEmpty();
        assertThat(metadata.getCategories()).isEmpty();
    }
}
