package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class CbxMetadataExtractorTest {

    private CbxMetadataExtractor extractor;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        extractor = new CbxMetadataExtractor();
        tempDir = Files.createTempDirectory("cbx_test_");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null) {
            // best-effort cleanup
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
        }
    }

    @Test
    void extractMetadata_fromCbz_withComicInfo_populatesFields() throws Exception {
        String xml = "" +
                "<ComicInfo>" +
                "  <Title>My Comic</Title>" +
                "  <Summary>A short summary</Summary>" +
                "  <Publisher>Indie</Publisher>" +
                "  <Series>Series X</Series>" +
                "  <Number>2.5</Number>" +
                "  <Count>12</Count>" +
                "  <Year>2020</Year><Month>7</Month><Day>14</Day>" +
                "  <PageCount>42</PageCount>" +
                "  <LanguageISO>en</LanguageISO>" +
                "  <Writer>Alice</Writer>" +
                "  <Penciller>Bob</Penciller>" +
                "  <Tags>action;adventure</Tags>" +
                "</ComicInfo>";

        File cbz = createCbz("with_meta.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("page1.jpg", new byte[]{1,2,3});
        }});

        BookMetadata md = extractor.extractMetadata(cbz);
        assertEquals("My Comic", md.getTitle());
        assertEquals("A short summary", md.getDescription());
        assertEquals("Indie", md.getPublisher());
        assertEquals("Series X", md.getSeriesName());
        assertEquals(2.5f, md.getSeriesNumber());
        assertEquals(Integer.valueOf(12), md.getSeriesTotal());
        assertEquals(LocalDate.of(2020,7,14), md.getPublishedDate());
        assertEquals(Integer.valueOf(42), md.getPageCount());
        assertEquals("en", md.getLanguage());
        assertTrue(md.getAuthors().contains("Alice"));
        assertTrue(md.getAuthors().contains("Bob"));
        assertTrue(md.getCategories().contains("action"));
        assertTrue(md.getCategories().contains("adventure"));
    }

    @Test
    void extractCover_fromCbz_usesComicInfoImageFile() throws Exception {
        String xml = "" +
                "<ComicInfo>" +
                "  <Pages>" +
                "    <Page Type=\"FrontCover\" ImageFile=\"images/002.jpg\"/>" +
                "  </Pages>" +
                "</ComicInfo>";

        byte[] img1 = createTestImage(Color.RED);
        byte[] img2 = createTestImage(Color.GREEN); // expect this one
        byte[] img3 = createTestImage(Color.BLUE);

        File cbz = createCbz("with_cover.cbz", new LinkedHashMap<>() {{
            put("ComicInfo.xml", xml.getBytes(StandardCharsets.UTF_8));
            put("images/001.jpg", img1);
            put("images/002.jpg", img2);
            put("images/003.jpg", img3);
        }});

        byte[] cover = extractor.extractCover(cbz);
        assertArrayEquals(img2, cover);
    }

    @Test
    void extractCover_fromCbz_fallbackAlphabeticalFirst() throws Exception {
        // No ComicInfo.xml, images intentionally added in unsorted order
        byte[] aPng = createTestImage(Color.YELLOW); // alphabetically first (A.png)
        byte[] bJpg = createTestImage(Color.MAGENTA);
        byte[] cJpeg = createTestImage(Color.CYAN);

        File cbz = createCbz("fallback.cbz", new LinkedHashMap<>() {{
            put("z/pageC.jpeg", cJpeg);
            put("A.png", aPng);           // should be chosen
            put("b.jpg", bJpg);
        }});

        byte[] cover = extractor.extractCover(cbz);
        assertArrayEquals(aPng, cover);
    }

    @Test
    void extractMetadata_nonArchive_fallbackTitle() throws Exception {
        Path txt = tempDir.resolve("Some Book Title.txt");
        Files.write(txt, "hello".getBytes(StandardCharsets.UTF_8));
        BookMetadata md = extractor.extractMetadata(txt.toFile());
        assertEquals("Some Book Title", md.getTitle());
    }

    // ---------- helpers ----------

    private File createCbz(String name, Map<String, byte[]> entries) throws IOException {
        Path out = tempDir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out.toFile()))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String entryName = e.getKey();
                byte[] data = e.getValue();
                ZipEntry ze = new ZipEntry(entryName);
                // set a fixed time to avoid platform-dependent headers
                ze.setTime(0L);
                zos.putNextEntry(ze);
                try (InputStream is = new ByteArrayInputStream(data)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return out.toFile();
    }

    private byte[] createTestImage(Color color) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        }
    }
}