package org.booklore.service;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.kobo.CbxConversionService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.writer.CbxMetadataWriter;
import org.booklore.service.reader.CbxReaderService;
import org.booklore.repository.BookRepository;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.util.UnrarHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Integration tests that feed a real RAR5 archive into the service layer
 * and verify junrar fails then the unrar CLI fallback kicks in.
 */
@ExtendWith(MockitoExtension.class)
class Rar5FallbackIntegrationTest {

    private static final Path RAR5_CBR = Path.of("src/test/resources/cbx/test-rar5.cbr");

    @BeforeAll
    static void checkUnrarAvailable() {
        assumeThat(UnrarHelper.isAvailable())
                .as("unrar binary must be on PATH to run these tests")
                .isTrue();
    }

    // -- CbxMetadataExtractor: extractMetadata fallback --

    @Test
    void metadataExtractor_extractsComicInfoFromRar5(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        CbxMetadataExtractor extractor = new CbxMetadataExtractor();
        BookMetadata metadata = extractor.extractMetadata(cbrCopy.toFile());

        assertThat(metadata.getTitle()).isEqualTo("Test RAR5 Comic");
        assertThat(metadata.getSeriesName()).isEqualTo("RAR5 Test Series");
        assertThat(metadata.getSeriesNumber()).isEqualTo(1.0f);
        assertThat(metadata.getAuthors()).contains("Test Author");
    }

    // -- CbxReaderService: getImageEntriesFromRar + streamEntryFromRar fallback --

    @Test
    void readerService_listsImagePagesFromRar5(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        BookEntity book = new BookEntity();
        book.setId(99L);
        BookRepository mockRepo = org.mockito.Mockito.mock(BookRepository.class);
        org.mockito.Mockito.when(mockRepo.findById(99L)).thenReturn(java.util.Optional.of(book));

        try (var fileUtilsStatic = org.mockito.Mockito.mockStatic(org.booklore.util.FileUtils.class)) {
            fileUtilsStatic.when(() -> org.booklore.util.FileUtils.getBookFullPath(book))
                    .thenReturn(cbrCopy);

            CbxReaderService readerService = new CbxReaderService(mockRepo);
            List<Integer> pages = readerService.getAvailablePages(99L);

            assertThat(pages).hasSize(3);
            assertThat(pages).containsExactly(1, 2, 3);
        }
    }

    @Test
    void readerService_streamsImageFromRar5(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        BookEntity book = new BookEntity();
        book.setId(99L);
        BookRepository mockRepo = org.mockito.Mockito.mock(BookRepository.class);
        org.mockito.Mockito.when(mockRepo.findById(99L)).thenReturn(java.util.Optional.of(book));

        try (var fileUtilsStatic = org.mockito.Mockito.mockStatic(org.booklore.util.FileUtils.class)) {
            fileUtilsStatic.when(() -> org.booklore.util.FileUtils.getBookFullPath(book))
                    .thenReturn(cbrCopy);

            CbxReaderService readerService = new CbxReaderService(mockRepo);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            readerService.streamPageImage(99L, 1, out);

            byte[] imageBytes = out.toByteArray();
            assertThat(imageBytes).hasSizeGreaterThan(0);
            assertThat(imageBytes[0]).isEqualTo((byte) 0xFF);
            assertThat(imageBytes[1]).isEqualTo((byte) 0xD8);
        }
    }

    // -- CbxConversionService: extractImagesFromRar fallback --

    @Test
    void conversionService_extractsImagesFromRar5(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        BookEntity book = new BookEntity();
        book.setId(99L);
        BookMetadataEntity meta = new BookMetadataEntity();
        meta.setTitle("Test RAR5 Comic");
        book.setMetadata(meta);

        CbxConversionService conversionService = new CbxConversionService();
        File epub = conversionService.convertCbxToEpub(cbrCopy.toFile(), tempDir.toFile(), book, 85);

        assertThat(epub).exists();
        assertThat(epub.length()).isGreaterThan(0);

        try (ZipFile epubZip = new ZipFile(epub)) {
            ZipEntry mimetype = epubZip.getEntry("mimetype");
            assertThat(mimetype).isNotNull();

            List<String> imageEntries = epubZip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.startsWith("OEBPS/Images/page-"))
                    .toList();
            assertThat(imageEntries).hasSize(3);
        }
    }

    // -- CbxMetadataWriter: loadFromRar + convertRarToZipArchive fallback --

    @Test
    void metadataWriter_convertsRar5ToCbzViaFallback(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        AppSettingService mockSettings = org.mockito.Mockito.mock(AppSettingService.class);
        var appSettings = new org.booklore.model.dto.settings.AppSettings();
        var persistenceSettings = new org.booklore.model.dto.settings.MetadataPersistenceSettings();
        var saveToFile = new org.booklore.model.dto.settings.MetadataPersistenceSettings.SaveToOriginalFile();
        var cbxSettings = new org.booklore.model.dto.settings.MetadataPersistenceSettings.FormatSettings();
        cbxSettings.setEnabled(true);
        cbxSettings.setMaxFileSizeInMb(500);
        saveToFile.setCbx(cbxSettings);
        persistenceSettings.setSaveToOriginalFile(saveToFile);
        appSettings.setMetadataPersistenceSettings(persistenceSettings);
        org.mockito.Mockito.when(mockSettings.getAppSettings()).thenReturn(appSettings);

        CbxMetadataWriter writer = new CbxMetadataWriter(mockSettings);

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Updated RAR5 Title");

        writer.saveMetadataToFile(cbrCopy.toFile(), metadata, null, null);

        Path cbzPath = tempDir.resolve("test.cbz");
        assertThat(cbzPath).exists();

        try (ZipFile resultZip = new ZipFile(cbzPath.toFile())) {
            ZipEntry comicInfo = resultZip.getEntry("ComicInfo.xml");
            assertThat(comicInfo).isNotNull();

            String xml = new String(resultZip.getInputStream(comicInfo).readAllBytes());
            assertThat(xml).contains("Updated RAR5 Title");

            long imageCount = resultZip.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.endsWith(".jpg"))
                    .count();
            assertThat(imageCount).isEqualTo(3);
        }
    }
}
