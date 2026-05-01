package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.service.ArchiveService;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("org.booklore.service.ArchiveService#isAvailable")
class CbxComicInfoComplianceTest {

    private static final Pattern EMPTY_LINES_PATTERN = Pattern.compile("(?s).*\\n\\s*\\n\\s*<.*");
    private CbxMetadataWriter writer;
    private Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        AppSettingService appSettingService = Mockito.mock(AppSettingService.class);
        AppSettings settings = new AppSettings();
        MetadataPersistenceSettings persistence = new MetadataPersistenceSettings();
        MetadataPersistenceSettings.SaveToOriginalFile save = new MetadataPersistenceSettings.SaveToOriginalFile();
        MetadataPersistenceSettings.FormatSettings cbx = new MetadataPersistenceSettings.FormatSettings();
        cbx.setEnabled(true);
        cbx.setMaxFileSizeInMb(100);
        save.setCbx(cbx);
        persistence.setSaveToOriginalFile(save);
        settings.setMetadataPersistenceSettings(persistence);
        Mockito.when(appSettingService.getAppSettings()).thenReturn(settings);

        writer = new CbxMetadataWriter(appSettingService, new ArchiveService());
        tempDir = Files.createTempDirectory("compliance_test_");
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
    void testComicInfoCompliance_OrderingAndFormatting() throws Exception {
        File cbz = createDummyCbz("compliance.cbz");
        
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Boys #70 - The Bloody Doors Off, Part 5");
        metadata.setSeriesName("The Boys");
        metadata.setSeriesNumber(70f);
        metadata.setSeriesTotal(14);
        metadata.setPublishedDate(LocalDate.of(2012, 9, 5));
        metadata.setPageCount(170);
        metadata.setDescription("<p><i>On his own and out of options, Hughie resorts to extreme measures...</i></p>");
        
        Set<TagEntity> tags = new HashSet<>();
        TagEntity t1 = TagEntity.builder().name("Superhero").build();
        tags.add(t1);
        metadata.setTags(tags);
        
        Set<MoodEntity> moods = new HashSet<>();
        MoodEntity m1 = MoodEntity.builder().name("Dark").build();
        moods.add(m1);
        metadata.setMoods(moods);
        
        metadata.setHardcoverBookId("547027");
        metadata.setIsbn13("9781606903735");
        metadata.setIsbn10("160690373X");
        metadata.setHardcoverRating(4.0d);

        writer.saveMetadataToFile(cbz, metadata, null, new MetadataClearFlags());

        String xmlContent = readComicInfoFromCbz(cbz);
        System.out.println("Generated XML:\n" + xmlContent);

        // 1. Verify Element Ordering (Crucial for XSD v2.0)
        // Title -> Series -> Number -> Count ... -> Summary -> Notes ... -> Web ...
        assertOrder(xmlContent, "Title", "Series");
        assertOrder(xmlContent, "Series", "Number");
        assertOrder(xmlContent, "Number", "Count");
        assertOrder(xmlContent, "Count", "Summary");
        assertOrder(xmlContent, "Summary", "Notes");
        assertOrder(xmlContent, "Notes", "Year");
        assertOrder(xmlContent, "Year", "Month");
        assertOrder(xmlContent, "Month", "Day");
        assertOrder(xmlContent, "Day", "Genre"); // Note: Genre is where we put categories usually, need to check if we set it.
        // We didn't set categories in this test, so Genre might be missing.
        assertOrder(xmlContent, "Day", "Web"); // Web comes later
        assertOrder(xmlContent, "Web", "PageCount");

        // 2. Verify Formatting
        assertFalse(EMPTY_LINES_PATTERN.matcher(xmlContent).matches(), "Should not have empty lines between elements");
        
        // 3. Verify HTML Stripping in Summary
        assertTrue(xmlContent.contains("<Summary>On his own and out of options, Hughie resorts to extreme measures...</Summary>"));
        assertFalse(xmlContent.contains("<p>"), "HTML tags should be removed from Summary");
        
        // 4. Verify Single Web URL
        assertTrue(xmlContent.contains("<Web>https://hardcover.app/books/547027</Web>"));
        // assertFalse(xmlContent.contains(","), "Web field should not be comma separated"); // Removed as other fields may contain commas
        
        // 5. Verify Notes Format
        assertTrue(xmlContent.contains("[BookLore:Moods] Dark"), "Notes should contain formatted custom metadata");

        
        // 6. Verify GTIN (v2.1)
        assertTrue(xmlContent.contains("<GTIN>9781606903735</GTIN>"));
    }

    private void assertOrder(String content, String tag1, String tag2) {
        int idx1 = content.indexOf("<" + tag1 + ">");
        int idx2 = content.indexOf("<" + tag2 + ">");
        
        // Skip if one of the tags is missing from output (optional fields)
        if (idx1 == -1 || idx2 == -1) return;
        
        assertTrue(idx1 < idx2, "Tag <" + tag1 + "> (pos " + idx1 + ") should appear before <" + tag2 + "> (pos " + idx2 + ")");
    }

    private File createDummyCbz(String name) throws Exception {
        File f = tempDir.resolve(name).toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f))) {
            ZipEntry ze = new ZipEntry("test.jpg");
            zos.putNextEntry(ze);
            zos.write(new byte[]{0});
            zos.closeEntry();
        }
        return f;
    }

    private String readComicInfoFromCbz(File cbz) throws Exception {
        try (ZipFile zip = new ZipFile(cbz)) {
            ZipEntry entry = zip.getEntry("ComicInfo.xml");
            try (var is = zip.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
