package org.grimmory.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EpubMetadataExtractorTest {

    private EpubMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new EpubMetadataExtractor();
    }

    private File createEpub(String opfContent) throws IOException {
        return createEpub(opfContent, "OEBPS/content.opf", null);
    }

    private File createEpub(String opfContent, String opfPath, byte[] coverImage) throws IOException {
        File epub = tempDir.resolve("test.epub").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
            zos.putNextEntry(new ZipEntry("mimetype"));
            zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            String containerXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="%s" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>""".formatted(opfPath);
            zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
            zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(opfPath));
            zos.write(opfContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            if (coverImage != null) {
                zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
                zos.write(coverImage);
                zos.closeEntry();
            }
        }
        return epub;
    }

    private String wrapOpf(String metadataContent) {
        return wrapOpf(metadataContent, "");
    }

    private String wrapOpf(String metadataContent, String manifestContent) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    %s
                  </metadata>
                  <manifest>
                    %s
                  </manifest>
                </package>""".formatted(metadataContent, manifestContent);
    }

    @Nested
    class BasicMetadataExtraction {

        @Test
        void extractsTitleDescriptionPublisherLanguage() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>The Great Book</dc:title>
                    <dc:description>A wonderful story</dc:description>
                    <dc:publisher>Acme Publishing</dc:publisher>
                    <dc:language>en</dc:language>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTitle()).isEqualTo("The Great Book");
            assertThat(metadata.getDescription()).isEqualTo("A wonderful story");
            assertThat(metadata.getPublisher()).isEqualTo("Acme Publishing");
            assertThat(metadata.getLanguage()).isEqualTo("en");
        }

        @Test
        void fallsBackToFilenameWhenTitleIsBlank() throws IOException {
            String opf = wrapOpf("<dc:title>   </dc:title>");
            File epub = createEpub(opf);
            BookMetadata metadata = extractor.extractMetadata(epub);

            assertThat(metadata.getTitle()).isEqualTo("test");
        }

        @Test
        void extractsCategories() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:subject>Fiction</dc:subject>
                    <dc:subject>Fantasy</dc:subject>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Fiction", "Fantasy");
        }

        @Test
        void returnsNullWhenContainerXmlIsMissing() throws IOException {
            File epub = tempDir.resolve("nocontainer.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            assertThat(extractor.extractMetadata(epub)).isNull();
        }

        @Test
        void returnsNullWhenContainerHasNoRootfile() throws IOException {
            File epub = tempDir.resolve("norootfile.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles/>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            assertThat(extractor.extractMetadata(epub)).isNull();
        }

        @Test
        void returnsNullWhenNoMetadataElement() throws IOException {
            String opf = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <manifest/>
                    </package>""";
            assertThat(extractor.extractMetadata(createEpub(opf))).isNull();
        }
    }

    @Nested
    class TitleAndSubtitleDetection {

        @Test
        void distinguishesMainTitleFromSubtitleViaRefines() throws IOException {
            String opf = wrapOpf("""
                    <dc:title id="t1">Main Title</dc:title>
                    <dc:title id="t2">The Subtitle</dc:title>
                    <meta refines="#t1" property="title-type">main</meta>
                    <meta refines="#t2" property="title-type">subtitle</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTitle()).isEqualTo("Main Title");
            assertThat(metadata.getSubtitle()).isEqualTo("The Subtitle");
        }

        @Test
        void titleWithoutIdSetDirectly() throws IOException {
            String opf = wrapOpf("<dc:title>Direct Title</dc:title>");
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTitle()).isEqualTo("Direct Title");
        }

        @Test
        void titleWithIdButNoRefinesDefaultsToMain() throws IOException {
            String opf = wrapOpf("""
                    <dc:title id="t1">Fallback Main</dc:title>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTitle()).isEqualTo("Fallback Main");
        }
    }

    @Nested
    class DateParsing {

        @Test
        void parsesYearOnly() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>2024</dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        }

        @Test
        void parsesIsoLocalDate() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>2023-06-15</dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2023, 6, 15));
        }

        @Test
        void parsesOffsetDateTime() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>2022-03-10T14:30:00+05:30</dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2022, 3, 10));
        }

        @Test
        void parsesIsoDateWithExtraContent() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>2021-12-25T00:00:00</dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2021, 12, 25));
        }

        @Test
        void fallsBackToDctermsModifiedWhenNoDate() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="dcterms:modified">2020-08-01T12:00:00Z</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2020, 8, 1));
        }

        @Test
        void unparsableDateReturnsNull() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>not-a-date</dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isNull();
        }

        @Test
        void blankDateReturnsNull() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:date>   </dc:date>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPublishedDate()).isNull();
        }
    }

    @Nested
    class IdentifierSchemeMapping {

        @Test
        void parsesIsbn13WithOpfScheme() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="ISBN">978-0-13-468599-1</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn13()).isEqualTo("978-0-13-468599-1");
        }

        @Test
        void parsesIsbn10WithOpfScheme() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="ISBN">0-13-468599-X</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn10()).isEqualTo("0-13-468599-X");
        }

        @Test
        void parsesUrnIsbnFormat() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>urn:isbn:9780134685991</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn13()).isEqualTo("9780134685991");
        }

        @Test
        void parsesIsbnPrefixFormat() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>isbn:9780134685991</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn13()).isEqualTo("9780134685991");
        }

        @ParameterizedTest
        @CsvSource({
                "GOODREADS, 12345",
                "AMAZON, B09XXX",
                "GOOGLE, gId123",
                "COMICVINE, cv456",
                "HARDCOVER, hc789",
                "LUBIMYCZYTAC, lu123",
                "RANOBEDB, rn456"
        })
        void parsesIdentifierByScheme(String scheme, String value) throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="%s">%s</dc:identifier>
                    """.formatted(scheme, value));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            switch (scheme) {
                case "GOODREADS" -> assertThat(metadata.getGoodreadsId()).isEqualTo(value);
                case "AMAZON" -> assertThat(metadata.getAsin()).isEqualTo(value);
                case "GOOGLE" -> assertThat(metadata.getGoogleId()).isEqualTo(value);
                case "COMICVINE" -> assertThat(metadata.getComicvineId()).isEqualTo(value);
                case "HARDCOVER" -> assertThat(metadata.getHardcoverId()).isEqualTo(value);
                case "LUBIMYCZYTAC" -> assertThat(metadata.getLubimyczytacId()).isEqualTo(value);
                case "RANOBEDB" -> assertThat(metadata.getRanobedbId()).isEqualTo(value);
            }
        }

        @Test
        void parsesHardcoverBookSchemes() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="HARDCOVERBOOK">hcb-1</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));
            assertThat(metadata.getHardcoverBookId()).isEqualTo("hcb-1");

            opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="HARDCOVER_BOOK_ID">hcb-2</dc:identifier>
                    """);
            metadata = extractor.extractMetadata(createEpub(opf));
            assertThat(metadata.getHardcoverBookId()).isEqualTo("hcb-2");
        }

        @Test
        void parsesCalibrePrefixFormatIdentifiers() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>amazon:B09XXX</dc:identifier>
                    <dc:identifier>goodreads:99999</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAsin()).isEqualTo("B09XXX");
            assertThat(metadata.getGoodreadsId()).isEqualTo("99999");
        }

        @Test
        void ignoresCalibreAndUuidPrefixes() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>calibre:some-uuid</dc:identifier>
                    <dc:identifier>uuid:some-other-uuid</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAsin()).isNull();
            assertThat(metadata.getGoodreadsId()).isNull();
        }

        @Test
        void isbn13WithSpaces() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier opf:scheme="ISBN">978 0 13 468599 1</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn13()).isEqualTo("978 0 13 468599 1");
        }
    }

    @Nested
    class AuthorRoleParsing {

        @Test
        void extractsAuthorWithOpfRole() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator opf:role="aut">Jane Author</dc:creator>
                    <dc:creator opf:role="ill">Bob Illustrator</dc:creator>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactly("Jane Author");
        }

        @Test
        void creatorWithoutRoleDefaultsToAuthor() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator>Simple Author</dc:creator>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactly("Simple Author");
        }

        @Test
        void creatorWithIdAndRefinesRole() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator id="c1">Refined Author</dc:creator>
                    <dc:creator id="c2">The Illustrator</dc:creator>
                    <meta refines="#c1" property="role">aut</meta>
                    <meta refines="#c2" property="role">ill</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactly("Refined Author");
        }

        @Test
        void creatorWithIdButNoRoleRefinesDefaultsToAuthor() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator id="c1">Default Author</dc:creator>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactly("Default Author");
        }

        @Test
        void multipleAuthors() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator opf:role="aut">Author One</dc:creator>
                    <dc:creator opf:role="aut">Author Two</dc:creator>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Author One", "Author Two");
        }

        @Test
        void multipleRolesForSameCreator() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:creator id="author">Charles Dickens</dc:creator>
                    <meta property="role" refines="#author">aut</meta>
                    <meta property="role" refines="#author">waw</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAuthors()).containsExactly("Charles Dickens");
        }
    }

    @Nested
    class SeriesExtraction {

        @Test
        void extractsBookloreSeriesMeta() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:series">The Dark Tower</meta>
                    <meta property="booklore:series_index">3.5</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesName()).isEqualTo("The Dark Tower");
            assertThat(metadata.getSeriesNumber()).isEqualTo(3.5f);
        }

        @Test
        void extractsCalibreSeriesMeta() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta name="calibre:series" content="Calibre Series"/>
                    <meta name="calibre:series_index" content="2"/>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesName()).isEqualTo("Calibre Series");
            assertThat(metadata.getSeriesNumber()).isEqualTo(2.0f);
        }

        @Test
        void extractsBelongsToCollection() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="belongs-to-collection">EPUB3 Collection</meta>
                    <meta property="group-position">5</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesName()).isEqualTo("EPUB3 Collection");
            assertThat(metadata.getSeriesNumber()).isEqualTo(5.0f);
        }

        @Test
        void firstSeriesSourceWins() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:series">First Series</meta>
                    <meta name="calibre:series" content="Second Series"/>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesName()).isEqualTo("First Series");
        }

        @Test
        void invalidSeriesIndexIgnored() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:series">Series</meta>
                    <meta property="booklore:series_index">not-a-number</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesName()).isEqualTo("Series");
            assertThat(metadata.getSeriesNumber()).isNull();
        }
    }

    @Nested
    class CalibreUserMetadata {

        @Test
        void extractsSubtitleFromUserMetadata() throws IOException {
            String json = """
                    {"#subtitle": {"#value#": "A Deep Dive"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSubtitle()).isEqualTo("A Deep Dive");
        }

        @Test
        void extractsPageCountFromUserMetadata() throws IOException {
            String json = """
                    {"#pagecount": {"#value#": "350"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isEqualTo(350);
        }

        @Test
        void extractsRatingsAndReviewCounts() throws IOException {
            String json = """
                    {
                      "#amazon_rating": {"#value#": "4.5"},
                      "#amazon_review_count": {"#value#": "1200"},
                      "#goodreads_rating": {"#value#": "4.2"},
                      "#goodreads_review_count": {"#value#": "5000"},
                      "#hardcover_rating": {"#value#": "4.0"},
                      "#hardcover_review_count": {"#value#": "300"},
                      "#lubimyczytac_rating": {"#value#": "3.8"},
                      "#ranobedb_rating": {"#value#": "4.1"}
                    }""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAmazonRating()).isEqualTo(4.5);
            assertThat(metadata.getAmazonReviewCount()).isEqualTo(1200);
            assertThat(metadata.getGoodreadsRating()).isEqualTo(4.2);
            assertThat(metadata.getGoodreadsReviewCount()).isEqualTo(5000);
            assertThat(metadata.getHardcoverRating()).isEqualTo(4.0);
            assertThat(metadata.getHardcoverReviewCount()).isEqualTo(300);
            assertThat(metadata.getLubimyczytacRating()).isEqualTo(3.8);
            assertThat(metadata.getRanobedbRating()).isEqualTo(4.1);
        }

        @Test
        void extractsAgeRatingWhenValid() throws IOException {
            String json = """
                    {"#age_rating": {"#value#": "13"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAgeRating()).isEqualTo(13);
        }

        @Test
        void ignoresInvalidAgeRating() throws IOException {
            String json = """
                    {"#age_rating": {"#value#": "7"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAgeRating()).isNull();
        }

        @Test
        void extractsContentRatingWhenValid() throws IOException {
            String json = """
                    {"#content_rating": {"#value#": "mature"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getContentRating()).isEqualTo("MATURE");
        }

        @Test
        void ignoresInvalidContentRating() throws IOException {
            String json = """
                    {"#content_rating": {"#value#": "nonsense"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getContentRating()).isNull();
        }

        @Test
        void skipsNullAndEmptyValues() throws IOException {
            String json = """
                    {
                      "#subtitle": {"#value#": null},
                      "#pagecount": {"#value#": ""},
                      "#series_total": {"other_key": "no_value"}
                    }""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSubtitle()).isNull();
            assertThat(metadata.getPageCount()).isNull();
            assertThat(metadata.getSeriesTotal()).isNull();
        }

        @Test
        void extractsMoodsFromUserMetadata() throws IOException {
            String json = """
                    {"#moods": {"#value#": "[\\"dark\\", \\"atmospheric\\"]"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("dark", "atmospheric");
        }

        @Test
        void extractsExtraTagsFromUserMetadata() throws IOException {
            String json = """
                    {"#extra_tags": {"#value#": "tag1, tag2, tag3"}}""";
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTags()).containsExactlyInAnyOrder("tag1", "tag2", "tag3");
        }

        @Test
        void malformedJsonHandledGracefully() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">{not valid json</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata).isNotNull();
            assertThat(metadata.getTitle()).isEqualTo("Book");
        }
    }

    @Nested
    class PageCountExtraction {

        @Test
        void extractsCalibrePagesMeta() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta name="calibre:pages" content="400"/>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isEqualTo(400);
        }

        @Test
        void extractsSchemaPagecount() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="schema:pagecount">250</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isEqualTo(250);
        }

        @Test
        void extractsMediaPagecount() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="media:pagecount">123</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isEqualTo(123);
        }

        @Test
        void extractsCalibreUserMetadataPagecount() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta name="calibre:user_metadata:#pagecount" content='{"#value#": 512}'/>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isEqualTo(512);
        }

        @Test
        void invalidPageCountIgnored() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta name="calibre:pages" content="not-a-number"/>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getPageCount()).isNull();
        }
    }

    @Nested
    class BookloreCustomMetadata {

        @Test
        void extractsBookloreAsin() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:asin">B09TEST</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAsin()).isEqualTo("B09TEST");
        }

        @Test
        void extractsBookloreGoodreadsId() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:goodreads_id">12345</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getGoodreadsId()).isEqualTo("12345");
        }

        @Test
        void extractsBookloreSubtitle() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:subtitle">A Companion Guide</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSubtitle()).isEqualTo("A Companion Guide");
        }

        @Test
        void extractsBookloreAgeRatingValid() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:age_rating">18</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAgeRating()).isEqualTo(18);
        }

        @Test
        void extractsBookloreAgeRatingInvalidIgnored() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:age_rating">15</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAgeRating()).isNull();
        }

        @Test
        void extractsBookloreContentRating() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:content_rating">EXPLICIT</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getContentRating()).isEqualTo("EXPLICIT");
        }

        @Test
        void extractsBookloreRatings() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:amazon_rating">4.7</meta>
                    <meta property="booklore:amazon_review_count">999</meta>
                    <meta property="booklore:goodreads_rating">4.3</meta>
                    <meta property="booklore:goodreads_review_count">10000</meta>
                    <meta property="booklore:hardcover_rating">4.1</meta>
                    <meta property="booklore:hardcover_review_count">500</meta>
                    <meta property="booklore:lubimyczytac_rating">3.9</meta>
                    <meta property="booklore:ranobedb_rating">4.4</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAmazonRating()).isEqualTo(4.7);
            assertThat(metadata.getAmazonReviewCount()).isEqualTo(999);
            assertThat(metadata.getGoodreadsRating()).isEqualTo(4.3);
            assertThat(metadata.getGoodreadsReviewCount()).isEqualTo(10000);
            assertThat(metadata.getHardcoverRating()).isEqualTo(4.1);
            assertThat(metadata.getHardcoverReviewCount()).isEqualTo(500);
            assertThat(metadata.getLubimyczytacRating()).isEqualTo(3.9);
            assertThat(metadata.getRanobedbRating()).isEqualTo(4.4);
        }

        @Test
        void extractsBookloreMoodsAndTags() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:moods">dark, eerie</meta>
                    <meta property="booklore:tags">must-read, classic</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("dark", "eerie");
            assertThat(metadata.getTags()).containsExactlyInAnyOrder("must-read", "classic");
        }

        @Test
        void extractsBookloreSeriesTotal() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="booklore:series_total">7</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getSeriesTotal()).isEqualTo(7);
        }
    }

    @Nested
    class MoodsAndTagsSeparationFromCategories {

        @Test
        void moodsAndTagsRemovedFromCategories() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:subject>Fiction</dc:subject>
                    <dc:subject>dark</dc:subject>
                    <dc:subject>must-read</dc:subject>
                    <meta property="booklore:moods">dark</meta>
                    <meta property="booklore:tags">must-read</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getCategories()).containsExactly("Fiction");
            assertThat(metadata.getMoods()).containsExactly("dark");
            assertThat(metadata.getTags()).containsExactly("must-read");
        }
    }

    @Nested
    class CoverExtraction {

        @Test
        void extractsCoverViaCoverImageProperty() throws IOException {
            byte[] coverBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02, 0x03};
            String opf = wrapOpf("", """
                    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                    """);
            File epub = createEpub(opf, "OEBPS/content.opf", coverBytes);
            byte[] result = extractor.extractCover(epub);

            assertThat(result).isEqualTo(coverBytes);
        }

        @Test
        void extractsCoverByHeuristicManifestSearch() throws IOException {
            byte[] coverBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
            String opf = wrapOpf("", """
                    <item id="cover-img" href="images/cover.jpg" media-type="image/jpeg"/>
                    """);
            File epub = createEpub(opf, "OEBPS/content.opf", coverBytes);
            byte[] result = extractor.extractCover(epub);

            assertThat(result).isEqualTo(coverBytes);
        }

        @Test
        void extractsCoverByZipHeuristic() throws IOException {
            byte[] coverBytes = new byte[]{0x10, 0x20, 0x30};
            File epub = tempDir.resolve("cover_zip.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"/>
                          <manifest>
                            <item id="text" href="chapter1.html" media-type="application/xhtml+xml"/>
                          </manifest>
                        </package>""";
                zos.putNextEntry(new ZipEntry("content.opf"));
                zos.write(opf.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("images/cover.jpg"));
                zos.write(coverBytes);
                zos.closeEntry();
            }

            byte[] result = extractor.extractCover(epub);
            assertThat(result).isEqualTo(coverBytes);
        }

        @Test
        void returnsNullForEpubWithNoCover() throws IOException {
            File epub = tempDir.resolve("nocover.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>No Cover Book</dc:title>
                          </metadata>
                          <manifest>
                            <item id="text" href="chapter1.html" media-type="application/xhtml+xml"/>
                          </manifest>
                        </package>""";
                zos.putNextEntry(new ZipEntry("content.opf"));
                zos.write(opf.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            byte[] result = extractor.extractCover(epub);
            assertThat(result).isNull();
        }
    }

    @Nested
    class PathResolution {

        @Test
        void resolvesCoverWithParentDirectorySegments() throws IOException {
            byte[] coverBytes = new byte[]{0x01, 0x02};
            File epub = tempDir.resolve("pathtest.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/subdir/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Path Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="../images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
                zos.putNextEntry(new ZipEntry("OEBPS/subdir/content.opf"));
                zos.write(opf.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
                zos.write(coverBytes);
                zos.closeEntry();
            }

            byte[] result = extractor.extractCover(epub);
            assertThat(result).isEqualTo(coverBytes);
        }

        @Test
        void resolvesAbsoluteHrefInZip() throws IOException {
            byte[] coverBytes = new byte[]{0x0A, 0x0B};
            File epub = tempDir.resolve("abstest.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Abs Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="/images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
                zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
                zos.write(opf.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("images/cover.jpg"));
                zos.write(coverBytes);
                zos.closeEntry();
            }

            byte[] result = extractor.extractCover(epub);
            assertThat(result).isEqualTo(coverBytes);
        }

        @Test
        void resolvesDotSegmentsInHref() throws IOException {
            byte[] coverBytes = new byte[]{0x0C};
            File epub = tempDir.resolve("dottest.epub").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(epub))) {
                zos.putNextEntry(new ZipEntry("mimetype"));
                zos.write("application/epub+zip".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String containerXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles>
                            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                          </rootfiles>
                        </container>""";
                zos.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zos.write(containerXml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                String opf = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <dc:title>Dot Test</dc:title>
                          </metadata>
                          <manifest>
                            <item id="cover" href="./images/../images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                          </manifest>
                        </package>""";
                zos.putNextEntry(new ZipEntry("OEBPS/content.opf"));
                zos.write(opf.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("OEBPS/images/cover.jpg"));
                zos.write(coverBytes);
                zos.closeEntry();
            }

            byte[] result = extractor.extractCover(epub);
            assertThat(result).isEqualTo(coverBytes);
        }
    }

    @Nested
    class OpfAtRootLevel {

        @Test
        void handlesOpfAtZipRoot() throws IOException {
            String opf = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                        <dc:title>Root OPF</dc:title>
                        <dc:creator opf:role="aut">Root Author</dc:creator>
                        <dc:date>2020</dc:date>
                      </metadata>
                      <manifest/>
                    </package>""";
            File epub = createEpub(opf, "content.opf", null);
            BookMetadata metadata = extractor.extractMetadata(epub);

            assertThat(metadata.getTitle()).isEqualTo("Root OPF");
            assertThat(metadata.getAuthors()).containsExactly("Root Author");
            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        }
    }

    @Nested
    class FullMetadataIntegration {

        @Test
        void extractsComprehensiveMetadata() throws IOException {
            String opf = wrapOpf("""
                    <dc:title id="t1">The Comprehensive Book</dc:title>
                    <dc:title id="t2">An Exhaustive Subtitle</dc:title>
                    <meta refines="#t1" property="title-type">main</meta>
                    <meta refines="#t2" property="title-type">subtitle</meta>
                    <dc:creator id="c1">Author One</dc:creator>
                    <dc:creator id="c2">Author Two</dc:creator>
                    <meta refines="#c1" property="role">aut</meta>
                    <meta refines="#c2" property="role">aut</meta>
                    <dc:description>A very detailed description.</dc:description>
                    <dc:publisher>Big Publisher</dc:publisher>
                    <dc:language>en</dc:language>
                    <dc:date>2023-11-15</dc:date>
                    <dc:subject>Science Fiction</dc:subject>
                    <dc:subject>Adventure</dc:subject>
                    <dc:identifier opf:scheme="ISBN">9781234567890</dc:identifier>
                    <dc:identifier>amazon:B09FULL</dc:identifier>
                    <dc:identifier>goodreads:88888</dc:identifier>
                    <meta property="booklore:series">The Epic Series</meta>
                    <meta property="booklore:series_index">3</meta>
                    <meta property="booklore:series_total">10</meta>
                    <meta property="booklore:moods">thrilling, suspenseful</meta>
                    <meta property="booklore:tags">award-winner</meta>
                    <meta property="booklore:age_rating">16</meta>
                    <meta property="booklore:content_rating">TEEN</meta>
                    <meta property="booklore:amazon_rating">4.8</meta>
                    <meta property="schema:pagecount">450</meta>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getTitle()).isEqualTo("The Comprehensive Book");
            assertThat(metadata.getSubtitle()).isEqualTo("An Exhaustive Subtitle");
            assertThat(metadata.getAuthors()).containsExactlyInAnyOrder("Author One", "Author Two");
            assertThat(metadata.getDescription()).isEqualTo("A very detailed description.");
            assertThat(metadata.getPublisher()).isEqualTo("Big Publisher");
            assertThat(metadata.getLanguage()).isEqualTo("en");
            assertThat(metadata.getPublishedDate()).isEqualTo(LocalDate.of(2023, 11, 15));
            assertThat(metadata.getCategories()).containsExactlyInAnyOrder("Science Fiction", "Adventure");
            assertThat(metadata.getIsbn13()).isEqualTo("9781234567890");
            assertThat(metadata.getAsin()).isEqualTo("B09FULL");
            assertThat(metadata.getGoodreadsId()).isEqualTo("88888");
            assertThat(metadata.getSeriesName()).isEqualTo("The Epic Series");
            assertThat(metadata.getSeriesNumber()).isEqualTo(3.0f);
            assertThat(metadata.getSeriesTotal()).isEqualTo(10);
            assertThat(metadata.getMoods()).containsExactlyInAnyOrder("thrilling", "suspenseful");
            assertThat(metadata.getTags()).containsExactly("award-winner");
            assertThat(metadata.getAgeRating()).isEqualTo(16);
            assertThat(metadata.getContentRating()).isEqualTo("TEEN");
            assertThat(metadata.getAmazonRating()).isEqualTo(4.8);
            assertThat(metadata.getPageCount()).isEqualTo(450);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void nonExistentFileReturnsNull() {
            File nonExistent = new File(tempDir.toFile(), "nonexistent.epub");
            assertThat(extractor.extractMetadata(nonExistent)).isNull();
            assertThat(extractor.extractCover(nonExistent)).isNull();
        }

        @Test
        void corruptZipReturnsNull() throws IOException {
            File corrupt = tempDir.resolve("corrupt.epub").toFile();
            try (FileOutputStream fos = new FileOutputStream(corrupt)) {
                fos.write(new byte[]{0x00, 0x01, 0x02, 0x03});
            }
            assertThat(extractor.extractMetadata(corrupt)).isNull();
            assertThat(extractor.extractCover(corrupt)).isNull();
        }

        @Test
        void calibreAllIdentifierPrefixes() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>mobi-asin:B09MOBI</dc:identifier>
                    <dc:identifier>hardcover:hc123</dc:identifier>
                    <dc:identifier>hardcover_book:hcb456</dc:identifier>
                    <dc:identifier>google:goo789</dc:identifier>
                    <dc:identifier>lubimyczytac:lub111</dc:identifier>
                    <dc:identifier>ranobedb:ran222</dc:identifier>
                    <dc:identifier>comicvine:cv333</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getAsin()).isEqualTo("B09MOBI");
            assertThat(metadata.getHardcoverId()).isEqualTo("hc123");
            assertThat(metadata.getHardcoverBookId()).isEqualTo("hcb456");
            assertThat(metadata.getGoogleId()).isEqualTo("goo789");
            assertThat(metadata.getLubimyczytacId()).isEqualTo("lub111");
            assertThat(metadata.getRanobedbId()).isEqualTo("ran222");
            assertThat(metadata.getComicvineId()).isEqualTo("cv333");
        }

        @Test
        void urnSchemeIdentifierParsing() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>urn:isbn:9780134685991</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn13()).isEqualTo("9780134685991");
        }

        @Test
        void isbn10viaUrnScheme() throws IOException {
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <dc:identifier>urn:isbn:0134685997</dc:identifier>
                    """);
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getIsbn10()).isEqualTo("0134685997");
        }

        @Test
        void validAgeRatingsAccepted() throws IOException {
            for (int validAge : Set.of(0, 6, 10, 13, 16, 18, 21)) {
                String opf = wrapOpf("""
                        <dc:title>Book</dc:title>
                        <meta property="booklore:age_rating">%d</meta>
                        """.formatted(validAge));
                BookMetadata metadata = extractor.extractMetadata(createEpub(opf));
                assertThat(metadata.getAgeRating()).as("Age rating %d should be valid", validAge).isEqualTo(validAge);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {"EVERYONE", "TEEN", "MATURE", "ADULT", "EXPLICIT"})
        void validContentRatingsAcceptedViaCalibre(String rating) throws IOException {
            String json = """
                    {"#content_rating": {"#value#": "%s"}}""".formatted(rating.toLowerCase());
            String opf = wrapOpf("""
                    <dc:title>Book</dc:title>
                    <meta property="calibre:user_metadata">%s</meta>
                    """.formatted(json));
            BookMetadata metadata = extractor.extractMetadata(createEpub(opf));

            assertThat(metadata.getContentRating()).isEqualTo(rating);
        }
    }
}
