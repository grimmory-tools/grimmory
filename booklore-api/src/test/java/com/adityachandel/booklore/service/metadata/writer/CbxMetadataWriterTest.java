package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CbxMetadataWriterTest {

    private CbxMetadataWriter writer;
    private Path tempDir;

    @BeforeEach
    void setup() throws Exception {
        writer = new CbxMetadataWriter();
        tempDir = Files.createTempDirectory("cbx_writer_test_");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
        }
    }

    @Test
    void getSupportedBookType_isCbx() {
        assertEquals(BookFileType.CBX, writer.getSupportedBookType());
    }

    @Test
    void writeMetadataToFile_cbz_updatesOrCreatesComicInfo_andPreservesOtherFiles() throws Exception {
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
        meta.setPublishedDate(LocalDate.of(2020,7,14));
        meta.setPageCount(42);
        meta.setLanguage("en");

        Set<AuthorEntity> authors = new HashSet<>();
        AuthorEntity aliceAuthor = new AuthorEntity();
        aliceAuthor.setName("Alice");
        AuthorEntity bobAuthor = new AuthorEntity();
        bobAuthor.setName("Bob");
        authors.add(aliceAuthor);
        authors.add(bobAuthor);
        meta.setAuthors(authors);
        Set<CategoryEntity> cats = new HashSet<>();
        CategoryEntity actionCat = new CategoryEntity();
        actionCat.setName("action");
        CategoryEntity adventureCat = new CategoryEntity();
        adventureCat.setName("adventure");
        cats.add(actionCat);
        cats.add(adventureCat);
        meta.setCategories(cats);

        // Execute
        writer.writeMetadataToFile(cbz, meta, null, new MetadataClearFlags());

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
    void writeMetadataToFile_cbz_updatesExistingComicInfo() throws Exception {
        // Create a CBZ *with* an existing ComicInfo.xml
        Path out = tempDir.resolve("with_meta.cbz");
        String xml = "<ComicInfo>\n" +
                "  <Title>Old Title</Title>\n" +
                "  <Summary>Old Summary</Summary>\n" +
                "</ComicInfo>";
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            put(zos, "ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put(zos, "a.jpg", new byte[]{1});
        }

        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("New Title");
        meta.setDescription("New Summary");

        writer.writeMetadataToFile(out.toFile(), meta, null, new MetadataClearFlags());

        try (ZipFile zip = new ZipFile(out.toFile())) {
            ZipEntry ci = zip.getEntry("ComicInfo.xml");
            Document doc = parseXml(zip.getInputStream(ci));
            assertEquals("New Title", text(doc, "Title"));
            assertEquals("New Summary", text(doc, "Summary"));
            // a.jpg should still exist
            assertNotNull(zip.getEntry("a.jpg"));
        }
    }

    // ------------- helpers -------------

    private static File createCbz(Path path, String[] imageNames) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path.toFile()))) {
            for (String name : imageNames) {
                put(zos, name, new byte[]{1,2,3});
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