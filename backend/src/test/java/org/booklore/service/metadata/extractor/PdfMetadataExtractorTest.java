package org.booklore.service.metadata.extractor;

import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.model.MetadataTag;
import org.grimmory.pdfium4j.model.PageSize;
import org.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

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
        try (PdfDocument doc = PdfDocument.create()) {
            doc.insertBlankPage(0, PageSize.A4);
            customizer.customize(doc);
            doc.save(file.toPath());
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
            doc.setXmpMetadata(xmp);
        });
    }

        private File createPdfWithRawXmp(String xmp) throws Exception {
          return createPdf(doc -> doc.setXmpMetadata(xmp));
        }

    @FunctionalInterface
    interface PdfCustomizer {
        void customize(PdfDocument doc) throws Exception;
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
        File pdf = createPdf(_ -> {});
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
                doc.setMetadata(MetadataTag.TITLE, "My Book Title");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("My Book Title");
        }

        @Test
        void blankTitle_fallsBackToFilename() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "   ");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getTitle()).isEqualTo("test");
        }

        @Test
        void extractsAuthorsSplitByComma() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata(MetadataTag.AUTHOR, "Alice, Bob");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        void extractsAuthorsSplitByAmpersand() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata(MetadataTag.AUTHOR, "Alice & Bob");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getAuthors()).containsExactlyInAnyOrder("Alice", "Bob");
        }

        @Test
        void extractsDescription_fromSubject() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata(MetadataTag.SUBJECT, "A great book about testing");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getDescription()).isEqualTo("A great book about testing");
        }

        @Test
        void extractsPublisher_fromEbxPublisher() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata("EBX_PUBLISHER", "Penguin Books");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getPublisher()).isEqualTo("Penguin Books");
        }

        @Test
        void extractsCreationDate_asPublishedDate() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                // PDF date format: D:YYYYMMDDHHmmSS
                doc.setMetadata(MetadataTag.CREATION_DATE, "D:20230615120000Z");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getPublishedDate()).isNotNull();
            assertThat(meta.getPublishedDate().getYear()).isEqualTo(2023);
            assertThat(meta.getPublishedDate().getMonthValue()).isEqualTo(6);
        }

        @Test
        void extractsKeywords_semicolonSeparated() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata(MetadataTag.KEYWORDS, "Fiction; Science; Adventure");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactlyInAnyOrder("Fiction", "Science", "Adventure");
        }

        @Test
        void extractsKeywords_commaSeparated() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata(MetadataTag.KEYWORDS, "Fiction, Science");
            });
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getCategories()).containsExactlyInAnyOrder("Fiction", "Science");
        }

        @Test
        void extractsCustomLanguageField() throws Exception {
            File pdf = createPdf(doc -> {
                doc.setMetadata(MetadataTag.TITLE, "T");
                doc.setMetadata("Language", "English");
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
                doc.setMetadata(MetadataTag.TITLE, "DocInfo Title");

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
                doc.setXmpMetadata(xmp);
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
        void extractsSeriesName() throws Exception {
            File pdf = createPdfWithXmp("""
                <booklore:seriesName>Wheel of Time</booklore:seriesName>
                """);
            BookMetadata meta = extractor.extractMetadata(pdf);
            assertThat(meta.getSeriesName()).isEqualTo("Wheel of Time");
        }

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
                // With PDFium4j we don't have easy drawing commands in this test,
                // but a blank page should still render to a valid JPEG.
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
                doc.setMetadata(MetadataTag.TITLE, "DocInfo Only");
                doc.setMetadata(MetadataTag.AUTHOR, "Solo Author");
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
                doc.setMetadata(MetadataTag.TITLE, "My Title");

                String xmp = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        <rdf:Description/>
                      </rdf:RDF>
                    </x:xmpmeta>
                    """;
                doc.setXmpMetadata(xmp);
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
            doc.setMetadata(MetadataTag.TITLE, "Fallback Title");
            doc.setMetadata(MetadataTag.KEYWORDS, "Keyword1; Keyword2");

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
            doc.setXmpMetadata(xmp);
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

    @Test
    void fullXmpPacketWithMultipleDescriptionsAndUnicode_extractsMetadata() throws Exception {
        File pdf = createPdfWithRawXmp("""
            <?xpacket begin="\uFEFF" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about=""
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <dc:title>
                <rdf:Alt>
                  <rdf:li xml:lang="x-default">\u00E1\u00E9\u0151\u00FA\u00E9\u0151\u00E1\u00FA\u00E9\u0151\u00E9\u0151\u00FArrvsevrsevser</rdf:li>
                </rdf:Alt>
              </dc:title>
              <dc:creator>
                <rdf:Seq>
                  <rdf:li>Andrzej Sapkowski</rdf:li>
                  <rdf:li>David French</rdf:li>
                </rdf:Seq>
              </dc:creator>
              <dc:description>
                <rdf:Alt>
                  <rdf:li xml:lang="x-default">World fantasy award lifetime achievement winner.</rdf:li>
                </rdf:Alt>
              </dc:description>
              <dc:subject>
                <rdf:Bag>
                  <rdf:li>Science Fiction &amp; Fantasy</rdf:li>
                  <rdf:li>Adventure</rdf:li>
                  <rdf:li>Science fiction</rdf:li>
                  <rdf:li>Adulte</rdf:li>
                  <rdf:li>Biography</rdf:li>
                  <rdf:li>Fantasy</rdf:li>
                  <rdf:li>Assassins</rdf:li>
                  <rdf:li>Aventure</rdf:li>
                  <rdf:li>Fiction</rdf:li>
                  <rdf:li>Humor</rdf:li>
                </rdf:Bag>
              </dc:subject>
              <dc:publisher>
                <rdf:Bag>
                  <rdf:li>Orbit</rdf:li>
                </rdf:Bag>
              </dc:publisher>
              <dc:language>
                <rdf:Bag>
                  <rdf:li>en</rdf:li>
                </rdf:Bag>
              </dc:language>
              <dc:date>
                <rdf:Seq>
                  <rdf:li>2013-11-06</rdf:li>
                </rdf:Seq>
              </dc:date>
            </rdf:Description>
            <rdf:Description rdf:about=""
                xmlns:booklore="http://booklore.org/metadata/1.0/">
              <booklore:googleId>GAg_swEACAAJ</booklore:googleId>
              <booklore:pageCount>329</booklore:pageCount>
              <booklore:hardcoverRating>3.9</booklore:hardcoverRating>
              <booklore:subtitle>\u0151\u00E1\u00FC\u0151\u00FC\u00E1\u00FC\u0151\u00E1\u00FC\u0151\u00E1\u00FC\u0151\u00E1\u00FC\u00E1</booklore:subtitle>
              <booklore:goodreadsRating>4.0</booklore:goodreadsRating>
              <booklore:seriesTotal>5</booklore:seriesTotal>
              <booklore:isbn13>9780316441636</booklore:isbn13>
              <booklore:isbn10>0678452202</booklore:isbn10>
              <booklore:seriesNumber>0.6</booklore:seriesNumber>
              <booklore:hardcoverBookId>461967</booklore:hardcoverBookId>
              <booklore:seriesName>The Witcher</booklore:seriesName>
              <booklore:goodreadsId>36099978</booklore:goodreadsId>
              <booklore:hardcoverId>season-of-storms</booklore:hardcoverId>
            </rdf:Description>
            <rdf:Description rdf:about=""
                xmlns:xmp="http://ns.adobe.com/xap/1.0/">
              <xmp:MetadataDate>2026-05-12T11:22:50.667354886Z</xmp:MetadataDate>
              <xmp:ModifyDate>2026-05-12T11:22:50.667354886Z</xmp:ModifyDate>
              <xmp:CreateDate>2013-11-06</xmp:CreateDate>
              <xmp:CreatorTool>Booklore</xmp:CreatorTool>
            </rdf:Description>
            <rdf:Description rdf:about=""
                xmlns:booklore="http://booklore.org/metadata/1.0/">
              <booklore:tags>
                <rdf:Bag>
                  <rdf:li>Loveable Characters</rdf:li>
                  <rdf:li>Not Diverse Characters</rdf:li>
                  <rdf:li>Plot Driven</rdf:li>
                  <rdf:li>Weak Character Development</rdf:li>
                </rdf:Bag>
              </booklore:tags>
            </rdf:Description>
            </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
            """);

        BookMetadata meta = extractor.extractMetadata(pdf);

        assertThat(meta.getTitle()).isEqualTo("\u00E1\u00E9\u0151\u00FA\u00E9\u0151\u00E1\u00FA\u00E9\u0151\u00E9\u0151\u00FArrvsevrsevser");
        assertThat(meta.getSubtitle()).isEqualTo("\u0151\u00E1\u00FC\u0151\u00FC\u00E1\u00FC\u0151\u00E1\u00FC\u0151\u00E1\u00FC\u0151\u00E1\u00FC\u00E1");
        assertThat(meta.getAuthors()).containsExactly("Andrzej Sapkowski", "David French");
        assertThat(meta.getDescription()).isEqualTo("World fantasy award lifetime achievement winner.");
        assertThat(meta.getCategories()).contains("Science Fiction & Fantasy", "Adventure", "Fantasy", "Humor");
        assertThat(meta.getPublisher()).isEqualTo("Orbit");
        assertThat(meta.getLanguage()).isEqualTo("en");
        assertThat(meta.getPublishedDate()).isEqualTo(java.time.LocalDate.of(2013, 11, 6));
        assertThat(meta.getSeriesName()).isEqualTo("The Witcher");
        assertThat(meta.getSeriesNumber()).isEqualTo(0.6f);
        assertThat(meta.getSeriesTotal()).isEqualTo(5);
        assertThat(meta.getIsbn13()).isEqualTo("9780316441636");
        assertThat(meta.getIsbn10()).isEqualTo("0678452202");
        assertThat(meta.getGoogleId()).isEqualTo("GAg_swEACAAJ");
        assertThat(meta.getGoodreadsId()).isEqualTo("36099978");
        assertThat(meta.getHardcoverId()).isEqualTo("season-of-storms");
        assertThat(meta.getHardcoverBookId()).isEqualTo("461967");
        assertThat(meta.getHardcoverRating()).isEqualTo(3.9);
        assertThat(meta.getGoodreadsRating()).isEqualTo(4.0);
        assertThat(meta.getTags()).containsExactlyInAnyOrder(
                "Loveable Characters",
                "Not Diverse Characters",
                "Plot Driven",
                "Weak Character Development"
        );
        assertThat(meta.getPageCount()).isEqualTo(1);
    }
}
