package org.booklore.service.metadata.writer;

import org.grimmory.pdfium4j.PdfDocument;
import org.grimmory.pdfium4j.model.PageSize;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.metadata.extractor.PdfMetadataExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

import org.booklore.test.RequiresPdfium;

@RequiresPdfium
class PdfMetadataWriterTest {

    private PdfMetadataWriter writer;
    private PdfMetadataExtractor extractor;
    private Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        AppSettingService appSettingService = Mockito.mock(AppSettingService.class);
        AppSettings settings = new AppSettings();
        MetadataPersistenceSettings persistence = new MetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile save = new MetadataPersistenceSettings.SaveToOriginalFile();
        MetadataPersistenceSettings.FormatSettings pdfSettings = new MetadataPersistenceSettings.FormatSettings();
        pdfSettings.setEnabled(true);
        pdfSettings.setMaxFileSizeInMb(100);
        save.setPdf(pdfSettings);
        persistence.setSaveToOriginalFile(save);
        settings.setMetadataPersistenceSettings(persistence);
        Mockito.when(appSettingService.getAppSettings()).thenReturn(settings);

        writer = new PdfMetadataWriter(appSettingService);
        extractor = new PdfMetadataExtractor();
        tempDir = Files.createTempDirectory("pdf_writer_test_");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception _) {
                        }
                    });
        }
    }

    @Test
    void getSupportedBookType_isPdf() {
        assertEquals(BookFileType.PDF, writer.getSupportedBookType());
    }

    @Test
    void saveAndReadMetadata_basicFields_roundTrip() throws Exception {
        File pdf = createEmptyPdf("basic.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setTitle("Dead Simple Python");
        meta.setDescription("A Python programming guide");
        meta.setPublisher("No Starch Press");
        meta.setPublishedDate(LocalDate.of(2022, 11, 22));
        meta.setLanguage("en");
        meta.setPageCount(754);

        List<AuthorEntity> authors = new ArrayList<>();
        AuthorEntity author = new AuthorEntity();
        author.setName("Jason C McDonald");
        authors.add(author);
        meta.setAuthors(authors);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("Dead Simple Python", result.getTitle());
        assertEquals("A Python programming guide", result.getDescription());
        assertEquals("No Starch Press", result.getPublisher());
        assertEquals("en", result.getLanguage());
        assertTrue(result.getAuthors().contains("Jason C McDonald"));
    }

    @Test
    void saveAndReadMetadata_validSeries_isWritten() throws Exception {
        File pdf = createEmptyPdf("series-valid.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setTitle("The Walking Dead #22");
        meta.setSeriesName("The Walking Dead");
        meta.setSeriesNumber(22f);
        meta.setSeriesTotal(193);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("The Walking Dead", result.getSeriesName());
        assertEquals(22f, result.getSeriesNumber());
        assertEquals(193, result.getSeriesTotal());
    }

    @Test
    void saveAndReadMetadata_seriesNameOnly_notWritten() throws Exception {
        File pdf = createEmptyPdf("series-name-only.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setTitle("Programming Book");
        meta.setSeriesName("Programming"); // Name without number - BROKEN DATA
        meta.setSeriesNumber(null);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        // Series should NOT be written since it's incomplete
        assertNull(result.getSeriesName(), "Series name should not be written without valid number");
        assertNull(result.getSeriesNumber(), "Series number should be null");
    }

    @Test
    void saveAndReadMetadata_seriesNumberZero_notWritten() throws Exception {
        File pdf = createEmptyPdf("series-zero.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setTitle("Test Book");
        meta.setSeriesName("Test Series");
        meta.setSeriesNumber(0f); // Zero is invalid

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        // Series should NOT be written since number is zero
        assertNull(result.getSeriesName());
        assertNull(result.getSeriesNumber());
    }

    @Test
    void saveAndReadMetadata_seriesNumberFormattedNicely() throws Exception {
        File pdf = createEmptyPdf("series-format.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setSeriesName("Test Series");
        meta.setSeriesNumber(22f); // Whole number

        writer.saveMetadataToFile(pdf, meta, null, null);

        // Read raw XMP to verify formatting
        String xmpContent = readXmpContent(pdf);
        assertTrue(xmpContent.contains("<booklore:seriesNumber>22</booklore:seriesNumber>"),
                "Series number should be formatted as '22' not '22.00'");
    }

    @Test
    void saveAndReadMetadata_seriesDecimalNumber_preserved() throws Exception {
        File pdf = createEmptyPdf("series-decimal.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setSeriesName("Test Series");
        meta.setSeriesNumber(1.5f);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals(1.5f, result.getSeriesNumber());
    }

    @Test
    void saveAndReadMetadata_goodreadsIdNormalized() throws Exception {
        File pdf = createEmptyPdf("goodreads-id.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setGoodreadsId("52555538-dead-simple-python"); // Full slug format

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("52555538", result.getGoodreadsId(),
                "Goodreads ID should be normalized to just the numeric part");
    }

    @Test
    void saveAndReadMetadata_goodreadsIdAlreadyNumeric_unchanged() throws Exception {
        File pdf = createEmptyPdf("goodreads-numeric.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setGoodreadsId("12345678");

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("12345678", result.getGoodreadsId());
    }

    @Test
    void saveAndReadMetadata_externalIds_roundTrip() throws Exception {
        File pdf = createEmptyPdf("external-ids.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setIsbn13("9781718500921");
        meta.setIsbn10("1718500920");
        meta.setGoogleId("MPBmEAAAQBAJ");
        meta.setGoodreadsId("52555538");
        meta.setHardcoverId("dead-simple-python");
        meta.setHardcoverBookId("547027");
        meta.setAsin("B08KGS5V1R");
        meta.setComicvineId("4000-123456");
        meta.setLubimyczytacId("123456");
        meta.setRanobedbId("7890");

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("9781718500921", result.getIsbn13());
        assertEquals("1718500920", result.getIsbn10());
        assertEquals("MPBmEAAAQBAJ", result.getGoogleId());
        assertEquals("52555538", result.getGoodreadsId());
        assertEquals("dead-simple-python", result.getHardcoverId());
        assertEquals("547027", result.getHardcoverBookId());
        assertEquals("B08KGS5V1R", result.getAsin());
        assertEquals("4000-123456", result.getComicvineId());
        assertEquals("123456", result.getLubimyczytacId());
        assertEquals("7890", result.getRanobedbId());
    }

    @Test
    void saveAndReadMetadata_ratings_roundTrip() throws Exception {
        File pdf = createEmptyPdf("ratings.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setGoodreadsRating(4.4);
        meta.setHardcoverRating(4.2);
        meta.setAmazonRating(4.5);
        meta.setLubimyczytacRating(8.5);
        meta.setRanobedbRating(7.8);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals(4.4, result.getGoodreadsRating(), 0.01);
        assertEquals(4.2, result.getHardcoverRating(), 0.01);
        assertEquals(4.5, result.getAmazonRating(), 0.01);
        assertEquals(8.5, result.getLubimyczytacRating(), 0.01);
        assertEquals(7.8, result.getRanobedbRating(), 0.01);
    }

    @Test
    void saveAndReadMetadata_zeroRatings_notWritten() throws Exception {
        File pdf = createEmptyPdf("zero-ratings.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setGoodreadsRating(0.0);
        meta.setHardcoverRating(0.0);

        writer.saveMetadataToFile(pdf, meta, null, null);

        String xmpContent = readXmpContent(pdf);
        assertFalse(xmpContent.contains("goodreadsRating"),
                "Zero ratings should not be written to XMP");
        assertFalse(xmpContent.contains("hardcoverRating"));
    }

    @Test
    void saveAndReadMetadata_tagsAndMoods_asRdfBags() throws Exception {
        File pdf = createEmptyPdf("tags-moods.pdf");

        BookMetadataEntity meta = createBasicMetadata();

        Set<TagEntity> tags = new HashSet<>();
        TagEntity tag1 = new TagEntity();
        tag1.setName("Python");
        TagEntity tag2 = new TagEntity();
        tag2.setName("Programming");
        tags.add(tag1);
        tags.add(tag2);
        meta.setTags(tags);

        Set<MoodEntity> moods = new HashSet<>();
        MoodEntity mood1 = new MoodEntity();
        mood1.setName("Educational");
        MoodEntity mood2 = new MoodEntity();
        mood2.setName("Technical");
        moods.add(mood1);
        moods.add(mood2);
        meta.setMoods(moods);

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertTrue(result.getTags().contains("Python"));
        assertTrue(result.getTags().contains("Programming"));
        assertTrue(result.getMoods().contains("Educational"));
        assertTrue(result.getMoods().contains("Technical"));
    }

    @Test
    void saveAndReadMetadata_subtitle_roundTrip() throws Exception {
        File pdf = createEmptyPdf("subtitle.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setSubtitle("Idiomatic Python for the Impatient Programmer");

        writer.saveMetadataToFile(pdf, meta, null, null);

        BookMetadata result = extractor.extractMetadata(pdf);
        assertEquals("Idiomatic Python for the Impatient Programmer", result.getSubtitle());
    }

    @Test
    void saveMetadata_dateFormat_isDateOnly() throws Exception {
        File pdf = createEmptyPdf("date-format.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setPublishedDate(LocalDate.of(2021, 2, 17));

        writer.saveMetadataToFile(pdf, meta, null, null);

        String xmpContent = readXmpContent(pdf);
        // CreateDate should be date-only format: 2021-02-17
        assertTrue(xmpContent.contains("<xmp:CreateDate>2021-02-17</xmp:CreateDate>"),
                "CreateDate should use date-only format (YYYY-MM-DD)");
    }

    @Test
    void saveMetadata_bookloreNamespaceUsed() throws Exception {
        File pdf = createEmptyPdf("namespace.pdf");

        BookMetadataEntity meta = createBasicMetadata();
        meta.setSeriesName("Test");
        meta.setSeriesNumber(1f);
        meta.setIsbn13("1234567890123");

        writer.saveMetadataToFile(pdf, meta, null, null);

        String xmpContent = readXmpContent(pdf);
        assertTrue(xmpContent.contains("xmlns:booklore=\"http://booklore.org/metadata/1.0/\""),
                "Booklore namespace should be declared");
        assertTrue(xmpContent.contains("<booklore:seriesName>Test</booklore:seriesName>"),
                "Series should be in booklore namespace");
        assertFalse(xmpContent.contains("calibre:"),
                "Calibre namespace should NOT be used");
    }

    @Test
    void saveMetadata_creatorTool_isBooklore() throws Exception {
        File pdf = createEmptyPdf("creator.pdf");

        BookMetadataEntity meta = createBasicMetadata();

        writer.saveMetadataToFile(pdf, meta, null, null);

        String xmpContent = readXmpContent(pdf);
        assertTrue(xmpContent.contains("<xmp:CreatorTool>Booklore</xmp:CreatorTool>"));
    }

    // ========== Helper Methods ==========

    private File createEmptyPdf(String name) throws IOException {
        File pdf = tempDir.resolve(name).toFile();
        try (PdfDocument doc = PdfDocument.create()) {
            doc.insertBlankPage(0, new PageSize(612, 792));
            doc.save(pdf.toPath());
        }
        return pdf;
    }

    private BookMetadataEntity createBasicMetadata() {
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Test Book");
        return meta;
    }

    private String readXmpContent(File pdf) throws IOException {
        try (PdfDocument doc = PdfDocument.open(pdf.toPath())) {
            String xmp = doc.xmpMetadataString();
            return xmp != null ? xmp : "";
        }
    }
}
