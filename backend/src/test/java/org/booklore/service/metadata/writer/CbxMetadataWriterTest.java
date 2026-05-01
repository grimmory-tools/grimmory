package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ComicCreatorRole;
import org.booklore.service.ArchiveService;
import org.booklore.service.appsettings.AppSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("org.booklore.service.ArchiveService#isAvailable")
class CbxMetadataWriterTest {

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
        tempDir = Files.createTempDirectory("cbx_writer_test_");
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
    void getSupportedBookType_isCbx() {
        assertEquals(BookFileType.CBX, writer.getSupportedBookType());
    }

    @Test
    void saveMetadataToFile_cbz_updatesOrCreatesComicInfo_andPreservesOtherFiles() throws Exception {
        // Create a CBZ without ComicInfo.xml and with a couple of images
        File cbz = createCbz(tempDir.resolve("sample.cbz"), new String[]{
                "images/002.jpg", "images/001.jpg"
        });

        // Prepare metadata
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("My Comic");
        meta.setDescription("Short desc");
        meta.setPublisher("Indie");
        meta.setSeriesName("Series X");
        meta.setSeriesNumber(2.5f);
        meta.setSeriesTotal(12);
        meta.setPublishedDate(LocalDate.of(2020, 7, 14));
        meta.setPageCount(42);
        meta.setLanguage("en");

        List<AuthorEntity> authors = new ArrayList<>();
        AuthorEntity aliceAuthor = AuthorEntity.builder().id(1L).name("Alice").build();
        AuthorEntity bobAuthor = AuthorEntity.builder().id(2L).name("Bob").build();
        authors.add(aliceAuthor);
        authors.add(bobAuthor);
        meta.setAuthors(authors);
        Set<CategoryEntity> cats = new HashSet<>();
        CategoryEntity actionCat = CategoryEntity.builder().id(1L).name("action").build();
        CategoryEntity adventureCat = CategoryEntity.builder().id(2L).name("adventure").build();
        cats.add(actionCat);
        cats.add(adventureCat);
        meta.setCategories(cats);

        // Execute
        writer.saveMetadataToFile(cbz, meta, null, new MetadataClearFlags());

        // Assert ComicInfo.xml exists and contains our fields
        try (ZipFile zip = new ZipFile(cbz)) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            assertNotNull(ci, "ComicInfo.xml should be present after write");

            Document doc = parseXml(zip.getInputStream(ci));
            String title = text(doc, "Title");
            String summary = text(doc, "Summary");
            String publisher = text(doc, "Publisher");
            String series = text(doc, "Series");
            String number = text(doc, "Number");
            String count = text(doc, "Count");
            String year = text(doc, "Year");
            String month = text(doc, "Month");
            String day = text(doc, "Day");
            String pageCount = text(doc, "PageCount");
            String lang = text(doc, "LanguageISO");
            String writerEl = text(doc, "Writer");
            String genre = text(doc, "Genre");

            assertEquals("My Comic", title);
            assertEquals("Short desc", summary);
            assertEquals("Indie", publisher);
            assertEquals("Series X", series);
            assertEquals("2.5", number);
            assertEquals("12", count);
            assertEquals("2020", year);
            assertEquals("7", month);
            assertEquals("14", day);
            assertEquals("42", pageCount);
            assertEquals("en", lang);
            if (writerEl != null) {
                assertTrue(writerEl.contains("Alice"));
                assertTrue(writerEl.contains("Bob"));
            }
            if (genre != null) {
                assertTrue(genre.toLowerCase().contains("action"));
                assertTrue(genre.toLowerCase().contains("adventure"));
            }

            // Ensure original image entries are preserved
            assertNotNull(zip.getEntry("images/001.jpg"));
            assertNotNull(zip.getEntry("images/002.jpg"));
        }
    }

    @Test
    void saveMetadataToFile_cbz_writesTagsRatingAndWebField() throws Exception {
        File cbz = createCbz(tempDir.resolve("tags_rating.cbz"), new String[]{"page1.jpg"});

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Rating Test");
        meta.setRating(8.4);
        meta.setGoodreadsId("12345");
        meta.setAsin("B00TEST123");
        
        Set<TagEntity> tags = new HashSet<>();
        TagEntity tag1 = TagEntity.builder().id(1L).name("Fantasy").build();
        TagEntity tag2 = TagEntity.builder().id(2L).name("Epic").build();
        tags.add(tag1);
        tags.add(tag2);
        meta.setTags(tags);

        Set<MoodEntity> moods = new HashSet<>();
        MoodEntity mood = MoodEntity.builder().id(1L).name("Dark").build();
        moods.add(mood);
        meta.setMoods(moods);

        writer.saveMetadataToFile(cbz, meta, null, new MetadataClearFlags());

        try (ZipFile zip = new ZipFile(cbz)) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            assertNotNull(ci);
            Document doc = parseXml(zip.getInputStream(ci));

            String notesVal = text(doc, "Notes");
            assertNotNull(notesVal);
            assertTrue(notesVal.contains("[BookLore:Tags]"));
            assertTrue(notesVal.contains("Fantasy"));
            assertTrue(notesVal.contains("Epic"));

            // Tags now written as dedicated element per Anansi v2.1
            String tagsVal = text(doc, "Tags");
            assertNotNull(tagsVal, "Tags should be written as standalone element per Anansi v2.1");
            assertTrue(tagsVal.contains("Fantasy") || tagsVal.contains("Epic"), "Tags should contain Fantasy or Epic");

            String rating = text(doc, "CommunityRating");
            assertNotNull(rating);
            assertEquals("4.2", rating);

            String web = text(doc, "Web");
            assertNotNull(web);
            assertTrue(web.contains("goodreads.com"));
            // assertTrue(web.contains("amazon.com")); // Only primary URL is stored in Web field now

            String notes = text(doc, "Notes");
            assertNotNull(notes);
            assertTrue(notes.contains("[BookLore:Moods]"));
            assertTrue(notes.contains("Dark"));
        }
    }

    @Test
    void saveMetadataToFile_cbz_updatesExistingComicInfo() throws Exception {
        // Create a CBZ *with* an existing ComicInfo.xml
        Path out = tempDir.resolve("with_meta.cbz");
        String xml = """
                <ComicInfo>
                  <Title>Old Title</Title>
                  <Summary>Old Summary</Summary>
                </ComicInfo>""";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            put(zos, "ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put(zos, "a.jpg", new byte[]{1});
        }

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("New Title");
        meta.setDescription("New Summary");

        writer.saveMetadataToFile(out.toFile(), meta, null, new MetadataClearFlags());

        try (ZipFile zip = new ZipFile(out.toFile())) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            Document doc = parseXml(zip.getInputStream(ci));
            assertEquals("New Title", text(doc, "Title"));
            assertEquals("New Summary", text(doc, "Summary"));
            // a.jpg should still exist
            assertNotNull(zip.getEntry("a.jpg"));
        }
    }

    @Test
    void saveMetadataToFile_ZipNamedAsCbr_ShouldUpdateMetadata() throws Exception {
        File zipAsCbr = createCbz(tempDir.resolve("mismatched.cbr"), new String[]{"page1.jpg"});
        Path zipAsCbz = tempDir.resolve("mismatched.cbz");

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Mismatched Title");

        writer.saveMetadataToFile(zipAsCbr, meta, null, new MetadataClearFlags());

        try (ZipFile zip = new ZipFile(zipAsCbz.toFile())) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            assertNotNull(ci, "ComicInfo.xml should be present");
            Document doc = parseXml(zip.getInputStream(ci));
            assertEquals("Mismatched Title", text(doc, "Title"));
            assertNotNull(zip.getEntry("page1.jpg"));
        }
    }

    @Test
    void saveMetadataToFile_cbz_writesComicSpecificMetadata() throws Exception {
        File cbz = createCbz(tempDir.resolve("comic_meta.cbz"), new String[]{"page1.jpg"});

        // Create metadata with ComicMetadataEntity
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Spider-Man #1");
        meta.setSeriesName("Spider-Man");
        meta.setSeriesNumber(1.0f);
        meta.setAgeRating(13); // Teen rating

        // Create ComicMetadataEntity with all comic-specific fields
        ComicMetadataEntity comic = ComicMetadataEntity.builder()
                .volumeNumber(2023)
                .alternateSeries("Amazing Spider-Man")
                .alternateIssue("700.1")
                .storyArc("Superior")
                .format("Single Issue")
                .imprint("Marvel Knights")
                .blackAndWhite(false)
                .manga(true)
                .readingDirection("RTL")
                .build();

        // Characters
        ComicCharacterEntity char1 = new ComicCharacterEntity();
        char1.setId(1L);
        char1.setName("Peter Parker");
        ComicCharacterEntity char2 = new ComicCharacterEntity();
        char2.setId(2L);
        char2.setName("Mary Jane");
        Set<ComicCharacterEntity> characters = new HashSet<>();
        characters.add(char1);
        characters.add(char2);
        comic.setCharacters(characters);

        // Teams
        ComicTeamEntity team1 = new ComicTeamEntity();
        team1.setId(1L);
        team1.setName("Avengers");
        Set<ComicTeamEntity> teams = new HashSet<>();
        teams.add(team1);
        comic.setTeams(teams);

        // Locations
        ComicLocationEntity loc1 = new ComicLocationEntity();
        loc1.setId(1L);
        loc1.setName("New York City");
        Set<ComicLocationEntity> locations = new HashSet<>();
        locations.add(loc1);
        comic.setLocations(locations);

        // Creators
        ComicCreatorEntity penciller = new ComicCreatorEntity();
        penciller.setId(1L);
        penciller.setName("John Romita Jr.");
        ComicCreatorEntity inker = new ComicCreatorEntity();
        inker.setId(2L);
        inker.setName("Klaus Janson");
        ComicCreatorMappingEntity pencillerMapping = ComicCreatorMappingEntity.builder()
                .creator(penciller)
                .role(ComicCreatorRole.PENCILLER)
                .comicMetadata(comic)
                .build();
        ComicCreatorMappingEntity inkerMapping = ComicCreatorMappingEntity.builder()
                .creator(inker)
                .role(ComicCreatorRole.INKER)
                .comicMetadata(comic)
                .build();
        Set<ComicCreatorMappingEntity> creatorMappings = new HashSet<>();
        creatorMappings.add(pencillerMapping);
        creatorMappings.add(inkerMapping);
        comic.setCreatorMappings(creatorMappings);

        meta.setComicMetadata(comic);

        writer.saveMetadataToFile(cbz, meta, null, new MetadataClearFlags());

        try (ZipFile zip = new ZipFile(cbz)) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            assertNotNull(ci, "ComicInfo.xml should be present");
            Document doc = parseXml(zip.getInputStream(ci));

            // Basic metadata
            assertEquals("Spider-Man #1", text(doc, "Title"));
            assertEquals("Spider-Man", text(doc, "Series"));

            // Comic-specific fields
            assertEquals("2023", text(doc, "Volume"));
            assertEquals("Amazing Spider-Man", text(doc, "AlternateSeries"));
            assertEquals("700.1", text(doc, "AlternateNumber"));
            assertEquals("Superior", text(doc, "StoryArc"));
            assertEquals("Single Issue", text(doc, "Format"));
            assertEquals("Marvel Knights", text(doc, "Imprint"));
            assertEquals("No", text(doc, "BlackAndWhite"));
            assertEquals("YesAndRightToLeft", text(doc, "Manga"));
            assertEquals("Teen", text(doc, "AgeRating"));

            // Characters, Teams, Locations
            String characters_str = text(doc, "Characters");
            assertNotNull(characters_str);
            assertTrue(characters_str.contains("Peter Parker"));
            assertTrue(characters_str.contains("Mary Jane"));

            String teams_str = text(doc, "Teams");
            assertNotNull(teams_str);
            assertTrue(teams_str.contains("Avengers"));

            String locations_str = text(doc, "Locations");
            assertNotNull(locations_str);
            assertTrue(locations_str.contains("New York City"));

            // Creators
            String penciller_str = text(doc, "Penciller");
            assertNotNull(penciller_str);
            assertTrue(penciller_str.contains("John Romita Jr."));

            String inker_str = text(doc, "Inker");
            assertNotNull(inker_str);
            assertTrue(inker_str.contains("Klaus Janson"));
        }
    }

    // ------------- helpers -------------

    private static File createCbz(Path path, String[] imageNames) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            for (String name : imageNames) {
                put(zos, name, new byte[]{1, 2, 3});
            }
        }
        return path.toFile();
    }

    private static void put(ZipOutputStream zos, String name, byte[] data) throws Exception {
        ZipEntry ze = new ZipEntry(name);
        ze.setTime(0L);
        zos.putNextEntry(ze);
        zos.write(data);
        zos.closeEntry();
    }

    private static Document parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(is);
    }

    private static String text(Document doc, String tag) {
        var list = doc.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }
}