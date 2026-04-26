package org.booklore.service.metadata.extractor;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

import org.booklore.test.RequiresPdfium;

@RequiresPdfium
class PdfMetadataExtractorTest {

    private PdfMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new PdfMetadataExtractor();
    }

    private File createPdf(PdfCustomizer customizer) throws Exception {
        File file = tempDir.resolve("test.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // empty page
            }
            customizer.customize(doc);
            doc.save(file);
        }
        return file;
    }

    private File createPdfWithXmp(String xmpFragment) throws Exception {
        return createPdf(doc -> {
            String xmp = """
                <?xml version="1.0" encoding="UTF-8"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description
                        xmlns:dc="http://purl.org/dc/elements/1.1/"
                        xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                        xmlns:xmpidq="http://ns.adobe.com/xmp/Identifier/qual/1.0/"
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace"
                        xmlns:calibreSI="http://calibre-ebook.com/xmp-namespace/seriesIndex"
                        xmlns:booklore="http://booklore.org/metadata/1.0/">
                      %s
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """.formatted(xmpFragment);
            PDMetadata metadata = new PDMetadata(doc);
            metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
            doc.getDocumentCatalog().setMetadata(metadata);
        });
    }

    @FunctionalInterface
    interface PdfCustomizer {
        void customize(PDDocument doc) throws Exception;
    }

    // --- Basic / edge cases ---

    @Test
    void extractMetadata_nonExistentFile_returnsEmptyMetadata() {
        File nonExistent = new File("/tmp/does_not_exist_ever.pdf");
        BookMetadata meta = extractor.extractMetadata(nonExistent);
        assertThat(meta).isNotNull();
    }

    @Test
    void extractMetadata_directoryInsteadOfFile_returnsEmptyMetadata() {
        BookMetadata meta = extractor.extractMetadata(tempDir.toFile());
        assertThat(meta).isNotNull();
    }

    @Test
    void extractMetadata_minimalPdf_usesFilenameAsTitle() throws Exception {
        File pdf = createPdf(ignored -> {});
        BookMetadata meta = extractor.extractMetadata(pdf);
        assertThat(meta.getTitle()).isEqualTo("test");
        assertThat(meta.getPageCount()).isEqualTo(1);
    }

    // --- Document info (non-XMP) ---

    @Nested
    class DocumentInfoTests {

        @Test
        void extractsTitle() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("My Book Title");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("My Book Title");
        }

        @Test
        void blankTitle_fallsBackToFilename() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("   ");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("test");
        }

        @Test
        void extractsAuthorsSplitByComma() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setAuthor("Alice, Bob");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        void extractsAuthorsSplitByAmpersand() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setAuthor("Alice & Bob");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        void extractsDescription_fromSubject() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setSubject("A great book about testing");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getDescription()).isEqualTo("A great book about testing");
        }

        @Test
        void extractsPublisher_fromEbxPublisher() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.getCOSObject().setString(COSName.getPDFName("EBX_PUBLISHER"), "Penguin Books");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getPublisher()).isEqualTo("Penguin Books");
        }

        @Test
        void extractsCreationDate_asPublishedDate() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                Calendar cal = new GregorianCalendar(2023, Calendar.JUNE, 15);
                info.setCreationDate(cal);
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getPublishedDate()).isNotNull();
            assertThat(meta.getPublishedDate().getYear()).isEqualTo(2023);
            assertThat(meta.getPublishedDate().getMonthValue()).isEqualTo(6);
        }

        @Test
        void extractsKeywords_semicolonSeparated() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setKeywords("Fiction; Science; Adventure");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactlyInAnyOrder("Fiction", "Science", "Adventure");
        }

        @Test
        void extractsKeywords_commaSeparated() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setKeywords("Fiction, Science");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactlyInAnyOrder("Fiction", "Science");
        }

        @Test
        void extractsCustomLanguageField() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("T");
                info.setCustomMetadataValue("Language", "English");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getLanguage()).isEqualTo("English");
        }
    }

    // --- Dublin Core XMP ---

    @Nested
    class DublinCoreTests {

        @Test
        void extractsTitle_fromDcTitle() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:title><rdf:Alt><rdf:li xml:lang="x-default">XMP Title</rdf:li></rdf:Alt></dc:title>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("XMP Title");
        }

        @Test
        void xmpTitle_overridesDocInfoTitle() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("DocInfo Title");
                doc.setDocumentInformation(info);

                String xmp = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        <rdf:Description xmlns:dc="http://purl.org/dc/elements/1.1/">
                          <dc:title><rdf:Alt><rdf:li xml:lang="x-default">XMP Title</rdf:li></rdf:Alt></dc:title>
                        </rdf:Description>
                      </rdf:RDF>
                    </x:xmpmeta>
                    """;
                PDMetadata metadata = new PDMetadata(doc);
                metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
                doc.getDocumentCatalog().setMetadata(metadata);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("XMP Title");
        }

        @Test
        void extractsDescription_fromDcDescription() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:description><rdf:Alt><rdf:li xml:lang="x-default">A fine description</rdf:li></rdf:Alt></dc:description>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getDescription()).isEqualTo("A fine description");
        }

        @Test
        void extractsPublisher_fromDcPublisher() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:publisher><rdf:Bag><rdf:li>HarperCollins</rdf:li></rdf:Bag></dc:publisher>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getPublisher()).isEqualTo("HarperCollins");
        }

        @Test
        void extractsLanguage_fromDcLanguage() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:language><rdf:Bag><rdf:li>en</rdf:li></rdf:Bag></dc:language>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getLanguage()).isEqualTo("en");
        }

        @Test
        void extractsAuthors_fromDcCreator() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:creator>
                  <rdf:Seq>
                    <rdf:li>Author One</rdf:li>
                    <rdf:li>Author Two</rdf:li>
                  </rdf:Seq>
                </dc:creator>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Author One", "Author Two");
        }

        @Test
        void extractsCategories_fromDcSubject() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:subject>
                  <rdf:Bag>
                    <rdf:li>Fantasy</rdf:li>
                    <rdf:li>Adventure</rdf:li>
                  </rdf:Bag>
                </dc:subject>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactlyInAnyOrder("Fantasy", "Adventure");
        }

        @Test
        void dcSubject_excludesMoodsAndTags() throws Exception {
            File pdf = createPdfWithXmp("""
                <dc:subject>
                  <rdf:Bag>
                    <rdf:li>Fantasy</rdf:li>
                    <rdf:li>Dark</rdf:li>
                    <rdf:li>Favorites</rdf:li>
                  </rdf:Bag>
                </dc:subject>
                <booklore:Moods>Dark</booklore:Moods>
                <booklore:Tags>Favorites</booklore:Tags>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactly("Fantasy");
        }
    }

    // --- Calibre XMP ---

    @Nested
    class CalibreTests {

        @Test
        void extractsSeriesName() throws Exception {
            File pdf = createPdfWithXmp("""
                <calibre:series><rdf:value>The Dark Tower</rdf:value></calibre:series>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesName()).isEqualTo("The Dark Tower");
        }

        @Test
        void extractsSeriesIndex_fullyQualified() throws Exception {
            File pdf = createPdfWithXmp("""
                <calibre:series>
                  <rdf:value>Series</rdf:value>
                  <calibreSI:series_index>3.5</calibreSI:series_index>
                </calibre:series>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesName()).isEqualTo("Series");
            assertThat(meta.getSeriesNumber()).isEqualTo(3.5f);
        }

        @Test
        void extractsSeriesIndex_withoutNamespacePrefix() throws Exception {
            File pdf = createPdfWithXmp("""
                <calibre:series>
                  <rdf:value>Series</rdf:value>
                  <series_index>2</series_index>
                </calibre:series>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesNumber()).isEqualTo(2.0f);
        }
    }

    // --- Booklore XMP ---

    @Nested
    class BookloreTests {

        @Test
        void extractsSeriesInfo() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:seriesName>Wheel of Time</booklore:seriesName>
                <booklore:seriesNumber>5</booklore:seriesNumber>
                <booklore:seriesTotal>14</booklore:seriesTotal>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesName()).isEqualTo("Wheel of Time");
            assertThat(meta.getSeriesNumber()).isEqualTo(5.0f);
            assertThat(meta.getSeriesTotal()).isEqualTo(14);
        }

        @Test
        void bookloreSeriesOverridesCalibre() throws Exception {
            File pdf = createPdfWithXmp("""
                <calibre:series><rdf:value>Calibre Series</rdf:value></calibre:series>
                <booklore:seriesName>Booklore Series</booklore:seriesName>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesName()).isEqualTo("Booklore Series");
        }

        @Test
        void extractsSubtitle_camelCase() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:subtitle>A New Beginning</booklore:subtitle>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSubtitle()).isEqualTo("A New Beginning");
        }

        @Test
        void extractsSubtitle_pascalCaseFallback() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:Subtitle>Legacy Subtitle</booklore:Subtitle>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSubtitle()).isEqualTo("Legacy Subtitle");
        }

        @Test
        void subtitle_camelCaseTakesPrecedenceOverPascalCase() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:subtitle>New</booklore:subtitle>
                <booklore:Subtitle>Legacy</booklore:Subtitle>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSubtitle()).isEqualTo("New");
        }

        @Test
        void extractsIsbns() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:isbn13>9780123456789</booklore:isbn13>
                <booklore:isbn10>0123456789</booklore:isbn10>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9780123456789");
            assertThat(meta.getIsbn10()).isEqualTo("0123456789");
        }

        @Test
        void extractsAllExternalIds() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:googleId>gid123</booklore:googleId>
                <booklore:goodreadsId>gr456</booklore:goodreadsId>
                <booklore:hardcoverId>hc789</booklore:hardcoverId>
                <booklore:hardcoverBookId>hcb012</booklore:hardcoverBookId>
                <booklore:asin>B00ASIN</booklore:asin>
                <booklore:comicvineId>cv345</booklore:comicvineId>
                <booklore:lubimyczytacId>lub678</booklore:lubimyczytacId>
                <booklore:ranobedbId>ran901</booklore:ranobedbId>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getGoogleId()).isEqualTo("gid123");
            assertThat(meta.getGoodreadsId()).isEqualTo("gr456");
            assertThat(meta.getHardcoverId()).isEqualTo("hc789");
            assertThat(meta.getHardcoverBookId()).isEqualTo("hcb012");
            assertThat(meta.getAsin()).isEqualTo("B00ASIN");
            assertThat(meta.getComicvineId()).isEqualTo("cv345");
            assertThat(meta.getLubimyczytacId()).isEqualTo("lub678");
            assertThat(meta.getRanobedbId()).isEqualTo("ran901");
        }
    }

    // --- Moods and Tags ---

    @Nested
    class MoodsAndTagsTests {

        @Test
        void extractsMoods_rdfBagFormat() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:moods>
                  <rdf:Bag>
                    <rdf:li>Dark</rdf:li>
                    <rdf:li>Suspenseful</rdf:li>
                  </rdf:Bag>
                </booklore:moods>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getMoods()).containsExactlyInAnyOrder("Dark", "Suspenseful");
        }

        @Test
        void extractsMoods_legacySemicolonFormat() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:Moods>Dark; Suspenseful; Eerie</booklore:Moods>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getMoods()).containsExactlyInAnyOrder("Dark", "Suspenseful", "Eerie");
        }

        @Test
        void moods_rdfBagTakesPrecedenceOverLegacy() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:moods>
                  <rdf:Bag>
                    <rdf:li>FromBag</rdf:li>
                  </rdf:Bag>
                </booklore:moods>
                <booklore:Moods>FromLegacy</booklore:Moods>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getMoods()).containsExactly("FromBag");
        }

        @Test
        void extractsTags_rdfBagFormat() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:tags>
                  <rdf:Bag>
                    <rdf:li>Favorites</rdf:li>
                    <rdf:li>ToRead</rdf:li>
                  </rdf:Bag>
                </booklore:tags>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTags()).containsExactlyInAnyOrder("Favorites", "ToRead");
        }

        @Test
        void extractsTags_legacySemicolonFormat() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:Tags>Favorites; Must Read</booklore:Tags>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTags()).containsExactlyInAnyOrder("Favorites", "Must Read");
        }

        @Test
        void tags_rdfBagTakesPrecedenceOverLegacy() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:tags>
                  <rdf:Bag>
                    <rdf:li>BagTag</rdf:li>
                  </rdf:Bag>
                </booklore:tags>
                <booklore:Tags>LegacyTag</booklore:Tags>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTags()).containsExactly("BagTag");
        }
    }

    // --- Ratings ---

    @Nested
    class RatingTests {

        @Test
        void extractsRatings_camelCase() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:amazonRating>4.5</booklore:amazonRating>
                <booklore:goodreadsRating>3.8</booklore:goodreadsRating>
                <booklore:hardcoverRating>4.1</booklore:hardcoverRating>
                <booklore:lubimyczytacRating>3.9</booklore:lubimyczytacRating>
                <booklore:ranobedbRating>4.0</booklore:ranobedbRating>
                <booklore:rating>5.0</booklore:rating>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAmazonRating()).isEqualTo(4.5);
            assertThat(meta.getGoodreadsRating()).isEqualTo(3.8);
            assertThat(meta.getHardcoverRating()).isEqualTo(4.1);
            assertThat(meta.getLubimyczytacRating()).isEqualTo(3.9);
            assertThat(meta.getRanobedbRating()).isEqualTo(4.0);
            assertThat(meta.getRating()).isEqualTo(5.0);
        }

        @Test
        void extractsRatings_pascalCaseFallback() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:AmazonRating>4.2</booklore:AmazonRating>
                <booklore:GoodreadsRating>3.7</booklore:GoodreadsRating>
                <booklore:Rating>4.0</booklore:Rating>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAmazonRating()).isEqualTo(4.2);
            assertThat(meta.getGoodreadsRating()).isEqualTo(3.7);
            assertThat(meta.getRating()).isEqualTo(4.0);
        }

        @Test
        void rating_camelCaseTakesPrecedenceOverPascalCase() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:amazonRating>4.5</booklore:amazonRating>
                <booklore:AmazonRating>3.0</booklore:AmazonRating>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAmazonRating()).isEqualTo(4.5);
        }

        @Test
        void invalidRatingValue_isIgnored() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:amazonRating>not_a_number</booklore:amazonRating>
                <booklore:goodreadsRating>3.5</booklore:goodreadsRating>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAmazonRating()).isNull();
            assertThat(meta.getGoodreadsRating()).isEqualTo(3.5);
        }
    }

    // --- Identifiers (xmp:Identifier/rdf:Bag) ---

    @Nested
    class IdentifierTests {

        @Test
        void extractsIsbn13_fromGenericIsbnScheme() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>978-0-13-468599-1</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9780134685991");
        }

        @Test
        void extractsIsbn10_fromGenericIsbnScheme_10digits() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>0-13-468599-X</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn10()).isEqualTo("013468599X");
        }

        @Test
        void specificIsbn13_overridesGenericIsbn() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>9780000000000</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN13</xmpidq:Scheme>
                      <rdf:value>9781234567890</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9781234567890");
        }

        @Test
        void specificIsbn10_overridesGenericIsbn() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>9780000000000</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN10</xmpidq:Scheme>
                      <rdf:value>0-123-45678-9</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn10()).isEqualTo("0123456789");
        }

        @Test
        void extractsGoogleAndAmazon_fromIdentifiers() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>GOOGLE</xmpidq:Scheme>
                      <rdf:value>google123</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>AMAZON</xmpidq:Scheme>
                      <rdf:value>B00ASIN123</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getGoogleId()).isEqualTo("google123");
            assertThat(meta.getAsin()).isEqualTo("B00ASIN123");
        }

        @Test
        void extractsGoodreadsComicvineRanobedb_fromIdentifiers() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>GOODREADS</xmpidq:Scheme>
                      <rdf:value>gr999</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>comicvine</xmpidq:Scheme>
                      <rdf:value>cv111</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>RanobeDB</xmpidq:Scheme>
                      <rdf:value>rn222</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getGoodreadsId()).isEqualTo("gr999");
            assertThat(meta.getComicvineId()).isEqualTo("cv111");
            assertThat(meta.getRanobedbId()).isEqualTo("rn222");
        }

        @Test
        void extractsHardcoverIds_fromIdentifiers() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>HARDCOVER</xmpidq:Scheme>
                      <rdf:value>hc_id</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>HARDCOVER_BOOK_ID</xmpidq:Scheme>
                      <rdf:value>hc_book_id</rdf:value>
                    </rdf:li>
                    <rdf:li>
                      <xmpidq:Scheme>LUBIMYCZYTAC</xmpidq:Scheme>
                      <rdf:value>lub_id</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getHardcoverId()).isEqualTo("hc_id");
            assertThat(meta.getHardcoverBookId()).isEqualTo("hc_book_id");
            assertThat(meta.getLubimyczytacId()).isEqualTo("lub_id");
        }

        @Test
        void isbnCleanup_removesHyphensAndSpaces() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>978 0 13 468599 1</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9780134685991");
        }

        @Test
        void genericIsbn_oddLength_fallsToIsbn13() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN</xmpidq:Scheme>
                      <rdf:value>12345</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("12345");
            assertThat(meta.getIsbn10()).isNull();
        }

        @Test
        void schemeLookup_isCaseInsensitive() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>Isbn13</xmpidq:Scheme>
                      <rdf:value>9781111111111</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9781111111111");
        }
    }

    // --- Identifier schemes are extracted last and take precedence over booklore namespace ---

    @Nested
    class IdentifierPrecedenceTests {

        @Test
        void identifierScheme_overridesBookloreGoogleId() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>GOOGLE</xmpidq:Scheme>
                      <rdf:value>from_identifier</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                <booklore:googleId>from_booklore</booklore:googleId>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getGoogleId()).isEqualTo("from_identifier");
        }

        @Test
        void identifierIsbn13_overridesBookloreIsbn13() throws Exception {
            File pdf = createPdfWithXmp("""
                <xmp:Identifier>
                  <rdf:Bag>
                    <rdf:li>
                      <xmpidq:Scheme>ISBN13</xmpidq:Scheme>
                      <rdf:value>9780000000000</rdf:value>
                    </rdf:li>
                  </rdf:Bag>
                </xmp:Identifier>
                <booklore:isbn13>9781111111111</booklore:isbn13>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getIsbn13()).isEqualTo("9780000000000");
        }
    }

    // --- Cover extraction ---

    @Nested
    class CoverExtractionTests {

        @Test
        void extractsCover_fromValidPdf() throws Exception {
            File pdf = createPdf(doc -> {
                PDPage page = doc.getPage(0);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                    cs.addRect(10, 10, 100, 100);
                    cs.fill();
                }
            });
            byte[] cover = extractor.extractCover(pdf);
            assertThat(cover).isNotNull();
            assertThat(cover.length).isGreaterThan(0);
            assertThat(cover[0]).isEqualTo((byte) 0xFF);
            assertThat(cover[1]).isEqualTo((byte) 0xD8);
        }

        @Test
        void extractCover_nonExistentFile_returnsNull() {
            File nonExistent = new File("/tmp/no_such_file_ever.pdf");
            byte[] cover = extractor.extractCover(nonExistent);
            assertThat(cover).isNull();
        }
    }

    // --- No XMP metadata ---

    @Nested
    class NoXmpTests {

        @Test
        void noXmpMetadata_usesOnlyDocInfo() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("DocInfo Only");
                info.setAuthor("Solo Author");
                doc.setDocumentInformation(info);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("DocInfo Only");
            assertThat(meta.getAuthors()).containsExactly("Solo Author");
            assertThat(meta.getIsbn13()).isNull();
            assertThat(meta.getSeriesName()).isNull();
        }

        @Test
        void emptyXmp_doesNotOverrideDocInfo() throws Exception {
            File pdf = createPdf(doc -> {
                PDDocumentInformation info = new PDDocumentInformation();
                info.setTitle("My Title");
                doc.setDocumentInformation(info);

                String xmp = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        <rdf:Description/>
                      </rdf:RDF>
                    </x:xmpmeta>
                    """;
                PDMetadata metadata = new PDMetadata(doc);
                metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
                doc.getDocumentCatalog().setMetadata(metadata);
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("My Title");
        }
    }

    // --- Invalid series number ---

    @Test
    void invalidSeriesNumber_isIgnored() throws Exception {
        File pdf = createPdfWithXmp("""
            <booklore:seriesName>Series</booklore:seriesName>
            <booklore:seriesNumber>not_a_number</booklore:seriesNumber>
            <booklore:seriesTotal>xyz</booklore:seriesTotal>
            """);
        BookMetadata meta = extractor.extractMetadata(pdf);
        assertThat(meta.getSeriesName()).isEqualTo("Series");
        assertThat(meta.getSeriesNumber()).isNull();
        assertThat(meta.getSeriesTotal()).isNull();
    }

    // --- Full integration-style test ---

    @Test
    void fullMetadata_allFieldsExtracted() throws Exception {
        File pdf = createPdf(doc -> {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("Fallback Title");
            info.setKeywords("Keyword1; Keyword2");
            doc.setDocumentInformation(info);

            String xmp = """
                <?xml version="1.0" encoding="UTF-8"?>
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description
                        xmlns:dc="http://purl.org/dc/elements/1.1/"
                        xmlns:xmp="http://ns.adobe.com/xap/1.0/"
                        xmlns:xmpidq="http://ns.adobe.com/xmp/Identifier/qual/1.0/"
                        xmlns:calibre="http://calibre-ebook.com/xmp-namespace"
                        xmlns:calibreSI="http://calibre-ebook.com/xmp-namespace/seriesIndex"
                        xmlns:booklore="http://booklore.org/metadata/1.0/">
                      <dc:title><rdf:Alt><rdf:li xml:lang="x-default">The Real Title</rdf:li></rdf:Alt></dc:title>
                      <dc:description><rdf:Alt><rdf:li xml:lang="x-default">A description</rdf:li></rdf:Alt></dc:description>
                      <dc:publisher><rdf:Bag><rdf:li>Big Publisher</rdf:li></rdf:Bag></dc:publisher>
                      <dc:language><rdf:Bag><rdf:li>en</rdf:li></rdf:Bag></dc:language>
                      <dc:creator><rdf:Seq><rdf:li>Author A</rdf:li><rdf:li>Author B</rdf:li></rdf:Seq></dc:creator>
                      <dc:subject><rdf:Bag><rdf:li>Sci-Fi</rdf:li><rdf:li>Thriller</rdf:li></rdf:Bag></dc:subject>
                      <booklore:seriesName>Epic Saga</booklore:seriesName>
                      <booklore:seriesNumber>3</booklore:seriesNumber>
                      <booklore:subtitle>The Return</booklore:subtitle>
                      <booklore:isbn13>9781234567890</booklore:isbn13>
                      <booklore:goodreadsRating>4.2</booklore:goodreadsRating>
                      <booklore:rating>4.0</booklore:rating>
                      <booklore:moods>
                        <rdf:Bag>
                          <rdf:li>Tense</rdf:li>
                          <rdf:li>Hopeful</rdf:li>
                        </rdf:Bag>
                      </booklore:moods>
                      <booklore:tags>
                        <rdf:Bag>
                          <rdf:li>Favorites</rdf:li>
                        </rdf:Bag>
                      </booklore:tags>
                      <xmp:Identifier>
                        <rdf:Bag>
                          <rdf:li>
                            <xmpidq:Scheme>AMAZON</xmpidq:Scheme>
                            <rdf:value>B00TEST</rdf:value>
                          </rdf:li>
                        </rdf:Bag>
                      </xmp:Identifier>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """;
            PDMetadata metadata = new PDMetadata(doc);
            metadata.importXMPMetadata(xmp.getBytes(StandardCharsets.UTF_8));
            doc.getDocumentCatalog().setMetadata(metadata);
        });

        BookMetadata meta = extractor.extractMetadata(pdf);

        assertThat(meta.getTitle()).isEqualTo("The Real Title");
        assertThat(meta.getSubtitle()).isEqualTo("The Return");
        assertThat(meta.getDescription()).isEqualTo("A description");
        assertThat(meta.getPublisher()).isEqualTo("Big Publisher");
        assertThat(meta.getLanguage()).isEqualTo("en");
        assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Author A", "Author B");
        assertThat(meta.getCategories()).containsExactlyInAnyOrder("Sci-Fi", "Thriller");
        assertThat(meta.getSeriesName()).isEqualTo("Epic Saga");
        assertThat(meta.getSeriesNumber()).isEqualTo(3.0f);
        assertThat(meta.getIsbn13()).isEqualTo("9781234567890");
        assertThat(meta.getGoodreadsRating()).isEqualTo(4.2);
        assertThat(meta.getRating()).isEqualTo(4.0);
        assertThat(meta.getMoods()).containsExactlyInAnyOrder("Tense", "Hopeful");
        assertThat(meta.getTags()).containsExactly("Favorites");
        assertThat(meta.getAsin()).isEqualTo("B00TEST");
        assertThat(meta.getPageCount()).isEqualTo(1);
    }
}
