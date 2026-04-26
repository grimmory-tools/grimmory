package org.grimmory.service.kobo;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import freemarker.template.TemplateException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.booklore.service.ArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CBX Conversion Integration Test")
@EnabledIf("org.booklore.service.ArchiveService#isAvailable")
class CbxConversionIntegrationTest {

    @TempDir
    Path tempDir;

    private CbxConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new CbxConversionService(new ArchiveService());
    }

    @Test
    @DisplayName("Should successfully convert CBZ to EPUB with valid structure")
    void convertCbzToEpub_MainConversionTest() throws IOException, TemplateException {
        File testCbzFile = createTestComicCbzFile();
        BookEntity bookMetadata = createTestBookMetadata();

        File epubFile = conversionService.convertCbxToEpub(testCbzFile, tempDir.toFile(), bookMetadata,85);

        assertThat(epubFile)
                .exists()
                .hasExtension("epub");

        assertThat(epubFile.length())
                .as("EPUB file should not be empty")
                .isGreaterThan(0);

        verifyEpubContents(epubFile, bookMetadata);
    }

    private File createTestComicCbzFile() throws IOException {
        File cbzFile = Files.createFile(tempDir.resolve("test-comic.cbz")).toFile();
        
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(cbzFile))) {
            String[] pageNames = {"cover.jpg", "page01.png", "page02.jpg"};
            Color[] colors = {Color.RED, Color.GREEN, Color.BLUE};
            
            for (int i = 0; i < 3; i++) {
                BufferedImage pageImage = createComicPageImage(400, 600, colors[i], "Page " + (i + 1));
                
                ZipArchiveEntry imageEntry = new ZipArchiveEntry(pageNames[i]);
                zipOut.putArchiveEntry(imageEntry);
                
                String format = pageNames[i].endsWith(".png") ? "png" : "jpg";
                ImageIO.write(pageImage, format, zipOut);
                zipOut.closeArchiveEntry();
            }
        }
        
        return cbzFile;
    }

    private BufferedImage createComicPageImage(int width, int height, Color bgColor, String text) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        g2d.setColor(bgColor);
        g2d.fillRect(0, 0, width, height);
        
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRect(10, 10, width - 20, height - 20);
        
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        
        g2d.drawString(text, (width - textWidth) / 2, (height + textHeight) / 2);
        
        g2d.dispose();
        return image;
    }

    private BookEntity createTestBookMetadata() {
        BookEntity book = new BookEntity();
        BookMetadataEntity metadata = new BookMetadataEntity();
        
        metadata.setTitle("Amazing Comic Adventures");
        metadata.setLanguage("en");
        
        book.setMetadata(metadata);
        return book;
    }

    private void verifyEpubContents(File epubFile, BookEntity expectedMetadata) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setFile(epubFile).get()) {
            List<String> entryNames = Collections.list(zipFile.getEntries())
                    .stream()
                    .map(ZipArchiveEntry::getName)
                    .toList();

            assertThat(entryNames)
                    .as("EPUB should contain required structure files")
                    .contains(
                            "mimetype",
                            "META-INF/container.xml",
                            "OEBPS/content.opf",
                            "OEBPS/toc.ncx",
                            "OEBPS/nav.xhtml",
                            "OEBPS/Styles/stylesheet.css"
                    );

            verifyMimetypeEntry(zipFile);

            long imageCount = entryNames.stream()
                    .filter(name -> name.startsWith("OEBPS/Images/") && name.endsWith(".jpg"))
                    .count();
            
            long pageCount = entryNames.stream()
                    .filter(name -> name.startsWith("OEBPS/Text/") && name.endsWith(".xhtml"))
                    .count();

            // Note: The EPUB contains 4 images because the conversion service duplicates the first image:
            // once as 'cover.jpg' (for the cover, referenced in the manifest but not in the spine)
            // and once as 'page-0001.jpg' (for the first comic page). Only 3 HTML pages are created,
            // one for each comic page image, since the cover image is not given its own HTML page.
            assertThat(imageCount)
                    .as("Should have converted all comic pages to images")
                    .isEqualTo(4);

            assertThat(pageCount)
                    .as("Should have created HTML page for each image (excluding cover)")
                    .isEqualTo(3);

            verifyContentOpf(zipFile, expectedMetadata);
        }
    }

    private void verifyMimetypeEntry(ZipFile zipFile) throws IOException {
        ZipArchiveEntry mimetypeEntry = zipFile.getEntry("mimetype");
        
        assertThat(mimetypeEntry)
                .as("Mimetype entry should exist")
                .isNotNull();

        assertThat(mimetypeEntry.getMethod())
                .as("Mimetype should be stored uncompressed")
                .isEqualTo(ZipArchiveEntry.STORED);

        try (InputStream stream = zipFile.getInputStream(mimetypeEntry)) {
            String content = new String(stream.readAllBytes());
            assertThat(content)
                    .as("Mimetype content should be correct")
                    .isEqualTo("application/epub+zip");
        }
    }

    private void verifyContentOpf(ZipFile zipFile, BookEntity expectedMetadata) throws IOException {
        ZipArchiveEntry contentOpfEntry = zipFile.getEntry("OEBPS/content.opf");
        
        assertThat(contentOpfEntry)
                .as("Content.opf should exist")
                .isNotNull();

        try (InputStream stream = zipFile.getInputStream(contentOpfEntry)) {
            String content = new String(stream.readAllBytes());
            
            assertThat(content)
                    .as("Content.opf should contain book title")
                    .contains(expectedMetadata.getMetadata().getTitle());
            
            assertThat(content)
                    .as("Content.opf should contain language")
                    .contains(expectedMetadata.getMetadata().getLanguage());

            assertThat(content)
                    .as("Content.opf should reference image files")
                    .contains("media-type=\"image/jpeg\"");
            
            assertThat(content)
                    .as("Content.opf should reference HTML pages")
                    .contains("media-type=\"application/xhtml+xml\"");

            assertThat(content)
                    .as("Content.opf should have spine entries")
                    .contains("<spine")
                    .contains("itemref");
        }
    }
}