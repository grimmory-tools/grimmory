package org.grimmory.service.kobo;

import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import freemarker.template.TemplateException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.grimmory.service.ArchiveService;
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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CBX Conversion Service Tests")
@EnabledIf("org.grimmory.service.ArchiveService#isAvailable")
class CbxConversionServiceTest {

    @TempDir
    Path tempDir;

    private CbxConversionService cbxConversionService;
    private File testCbzFile;
    private BookEntity testBookEntity;

    @BeforeEach
    void setUp() throws IOException {
        cbxConversionService = new CbxConversionService(new ArchiveService());
        testCbzFile = createTestCbzFile();
        testBookEntity = createTestBookEntity();
    }

    @Test
    void convertCbxToEpub_WithValidCbzFile_ShouldGenerateValidEpub() throws IOException, TemplateException {
        File epubFile = cbxConversionService.convertCbxToEpub(testCbzFile, tempDir.toFile(), testBookEntity,85);

        assertThat(epubFile).exists();
        assertThat(epubFile.getName()).endsWith(".epub");
        assertThat(epubFile.length()).isGreaterThan(0);

        verifyEpubStructure(epubFile);
    }

    @Test
    void convertCbxToEpub_WithNullCbxFile_ShouldThrowException() {
        assertThatThrownBy(() -> cbxConversionService.convertCbxToEpub(null, tempDir.toFile(), testBookEntity,85))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CBX file");
    }

    @Test
    void convertCbxToEpub_WithNonExistentFile_ShouldThrowException() {
        File nonExistentFile = new File(tempDir.toFile(), "non-existent.cbz");

        assertThatThrownBy(() -> cbxConversionService.convertCbxToEpub(nonExistentFile, tempDir.toFile(), testBookEntity,85))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CBX file");
    }

    @Test
    void convertCbxToEpub_WithUnsupportedFileFormat_ShouldThrowException() throws IOException {
        File unsupportedFile = Files.createFile(tempDir.resolve("test.txt")).toFile();

        assertThatThrownBy(() -> cbxConversionService.convertCbxToEpub(unsupportedFile, tempDir.toFile(), testBookEntity,85))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file format");
    }

    @Test
    void convertCbxToEpub_WithNullTempDir_ShouldThrowException() {
        assertThatThrownBy(() -> cbxConversionService.convertCbxToEpub(testCbzFile, null, testBookEntity,85))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid temp directory");
    }

    @Test
    void convertCbxToEpub_WithEmptyCbzFile_ShouldThrowException() throws IOException {
        File emptyCbzFile = createEmptyCbzFile();

        assertThatThrownBy(() -> cbxConversionService.convertCbxToEpub(emptyCbzFile, tempDir.toFile(), testBookEntity,85))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No valid images found");
    }

    @Test
    void convertCbxToEpub_WithNullBookEntity_ShouldUseDefaultMetadata() throws IOException, TemplateException {
        File epubFile = cbxConversionService.convertCbxToEpub(testCbzFile, tempDir.toFile(), null,85);

        assertThat(epubFile).exists();
        verifyEpubStructure(epubFile);
    }

    @Test
    void convertCbxToEpub_WithMultipleImages_ShouldPreservePageOrder() throws IOException, TemplateException {
        File multiPageCbzFile = createMultiPageCbzFile();

        File epubFile = cbxConversionService.convertCbxToEpub(multiPageCbzFile, tempDir.toFile(), testBookEntity,85);

        assertThat(epubFile).exists();
        verifyPageOrderInEpub(epubFile, 5);
    }

    @Test
    void convertCbxToEpub_WithZipNamedAsCbr_ShouldGenerateValidEpub() throws IOException, TemplateException {
        File zipAsCbr = new File(tempDir.toFile(), "fake.cbr");
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(zipAsCbr))) {
            BufferedImage testImage = createTestImage("Page 1", Color.RED);
            ZipArchiveEntry imageEntry = new ZipArchiveEntry("page01.png");
            zipOut.putArchiveEntry(imageEntry);
            ImageIO.write(testImage, "png", zipOut);
            zipOut.closeArchiveEntry();
        }

        File epubFile = cbxConversionService.convertCbxToEpub(zipAsCbr, tempDir.toFile(), testBookEntity, 85);

        assertThat(epubFile).exists();
        verifyEpubStructure(epubFile);
    }

    private File createTestCbzFile() throws IOException {
        File cbzFile = Files.createFile(tempDir.resolve("test-comic.cbz")).toFile();
        
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(cbzFile))) {
            BufferedImage testImage = createTestImage("Page 1", Color.RED);
            
            ZipArchiveEntry imageEntry = new ZipArchiveEntry("page01.png");
            zipOut.putArchiveEntry(imageEntry);
            ImageIO.write(testImage, "png", zipOut);
            zipOut.closeArchiveEntry();
        }
        
        return cbzFile;
    }

    private File createEmptyCbzFile() throws IOException {
        File cbzFile = Files.createFile(tempDir.resolve("empty-comic.cbz")).toFile();
        
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(cbzFile))) {
            ZipArchiveEntry textEntry = new ZipArchiveEntry("readme.txt");
            zipOut.putArchiveEntry(textEntry);
            zipOut.write("This is not an image".getBytes());
            zipOut.closeArchiveEntry();
        }
        
        return cbzFile;
    }

    private File createMultiPageCbzFile() throws IOException {
        File cbzFile = Files.createFile(tempDir.resolve("multi-page-comic.cbz")).toFile();
        
        try (ZipArchiveOutputStream zipOut = new ZipArchiveOutputStream(new FileOutputStream(cbzFile))) {
            Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA};
            
            for (int i = 0; i < 5; i++) {
                BufferedImage testImage = createTestImage("Page " + (i + 1), colors[i]);
                
                ZipArchiveEntry imageEntry = new ZipArchiveEntry(String.format("page%02d.png", i + 1));
                zipOut.putArchiveEntry(imageEntry);
                ImageIO.write(testImage, "png", zipOut);
                zipOut.closeArchiveEntry();
            }
        }
        
        return cbzFile;
    }

    private BufferedImage createTestImage(String text, Color backgroundColor) {
        BufferedImage image = new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setColor(backgroundColor);
        g2d.fillRect(0, 0, 200, 300);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString(text, 50, 150);
        
        g2d.dispose();
        return image;
    }

    private BookEntity createTestBookEntity() {
        BookEntity bookEntity = new BookEntity();
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Test Comic Book");
        metadata.setLanguage("en");
        bookEntity.setMetadata(metadata);
        return bookEntity;
    }

    private void verifyEpubStructure(File epubFile) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setFile(epubFile).get()) {
            List<ZipArchiveEntry> entries = Collections.list(zipFile.getEntries());
            
            assertThat(entries).extracting(ZipArchiveEntry::getName)
                    .contains(
                            "mimetype",
                            "META-INF/container.xml",
                            "OEBPS/content.opf",
                            "OEBPS/toc.ncx",
                            "OEBPS/nav.xhtml",
                            "OEBPS/Styles/stylesheet.css"
                    );

            ZipArchiveEntry mimetypeEntry = zipFile.getEntry("mimetype");
            assertThat(mimetypeEntry).isNotNull();
            assertThat(mimetypeEntry.getMethod()).isEqualTo(ZipArchiveEntry.STORED);

            try (InputStream mimetypeStream = zipFile.getInputStream(mimetypeEntry)) {
                String mimetypeContent = new String(mimetypeStream.readAllBytes());
                assertThat(mimetypeContent).isEqualTo("application/epub+zip");
            }

            assertThat(entries).anyMatch(entry -> entry.getName().startsWith("OEBPS/Images/"));
            assertThat(entries).anyMatch(entry -> entry.getName().startsWith("OEBPS/Text/"));
            assertThat(entries).anyMatch(entry -> entry.getName().endsWith(".jpg"));
            assertThat(entries).anyMatch(entry -> entry.getName().endsWith(".xhtml"));
        }
    }

    private void verifyPageOrderInEpub(File epubFile, int expectedPageCount) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setFile(epubFile).get()) {
            List<ZipArchiveEntry> imageEntries = Collections.list(zipFile.getEntries()).stream()
                    .filter(entry -> entry.getName().startsWith("OEBPS/Images/page-"))
                    .sorted(Comparator.comparing(ZipArchiveEntry::getName))
                    .toList();

            List<ZipArchiveEntry> htmlEntries = Collections.list(zipFile.getEntries()).stream()
                    .filter(entry -> entry.getName().startsWith("OEBPS/Text/page-"))
                    .sorted(Comparator.comparing(ZipArchiveEntry::getName))
                    .toList();

            assertThat(imageEntries).hasSize(expectedPageCount);
            assertThat(htmlEntries).hasSize(expectedPageCount);

            for (int i = 0; i < expectedPageCount; i++) {
                String expectedImageName = String.format("OEBPS/Images/page-%04d.jpg", i + 1);
                String expectedHtmlName = String.format("OEBPS/Text/page-%04d.xhtml", i + 1);
                
                assertThat(imageEntries.get(i).getName()).isEqualTo(expectedImageName);
                assertThat(htmlEntries.get(i).getName()).isEqualTo(expectedHtmlName);
            }
        }
    }
}