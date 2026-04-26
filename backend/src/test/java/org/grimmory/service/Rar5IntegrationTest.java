package org.grimmory.service;

import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.settings.AppSettings;
import org.grimmory.model.dto.settings.MetadataPersistenceSettings;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.service.kobo.CbxConversionService;
import org.grimmory.service.metadata.extractor.CbxMetadataExtractor;
import org.grimmory.service.metadata.writer.CbxMetadataWriter;
import org.grimmory.service.reader.CbxReaderService;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.appsettings.AppSettingService;
import org.grimmory.service.reader.ChapterCacheService;
import org.grimmory.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that feed a real RAR5 archive into the service layer
 */
@EnabledIf("org.grimmory.service.ArchiveService#isAvailable")
@ExtendWith(MockitoExtension.class)
class Rar5IntegrationTest {

    private static final Path RAR5_CBR = Path.of("src/test/resources/cbx/test-rar5.cbr");

    // -- CbxMetadataExtractor: extractMetadata fallback --

    @Test
    void metadataExtractor_extractsComicInfoFromRar5(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        CbxMetadataExtractor extractor = new CbxMetadataExtractor(new ArchiveService());
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
        org.mockito.Mockito.when(mockRepo.findByIdForStreaming(99L)).thenReturn(java.util.Optional.of(book));
        ChapterCacheService mockCache = org.mockito.Mockito.mock(ChapterCacheService.class);

        try (var fileUtilsStatic = org.mockito.Mockito.mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(book))
                    .thenReturn(cbrCopy);

            CbxReaderService readerService = new CbxReaderService(mockRepo, new ArchiveService(), mockCache);
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
        org.mockito.Mockito.when(mockRepo.findByIdForStreaming(99L)).thenReturn(java.util.Optional.of(book));
        ChapterCacheService mockCache = org.mockito.Mockito.mock(ChapterCacheService.class);

        try (
            var fileUtilsStatic = org.mockito.Mockito.mockStatic(FileUtils.class)
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(book))
                    .thenReturn(cbrCopy);

            CbxReaderService readerService = new CbxReaderService(mockRepo, new ArchiveService(), mockCache);
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

        CbxConversionService conversionService = new CbxConversionService(new ArchiveService());
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
    void metadataWriter_convertsRar5ToCbz(@TempDir Path tempDir) throws Exception {
        Path cbrCopy = tempDir.resolve("test.cbr");
        Files.copy(RAR5_CBR, cbrCopy);

        AppSettingService mockSettings = org.mockito.Mockito.mock(AppSettingService.class);
        var appSettings = new AppSettings();
        var persistenceSettings = new MetadataPersistenceSettings();
        var saveToFile = new MetadataPersistenceSettings.SaveToOriginalFile();
        var cbxSettings = new MetadataPersistenceSettings.FormatSettings();
        cbxSettings.setEnabled(true);
        cbxSettings.setMaxFileSizeInMb(500);
        saveToFile.setCbx(cbxSettings);
        persistenceSettings.setSaveToOriginalFile(saveToFile);
        appSettings.setMetadataPersistenceSettings(persistenceSettings);
        org.mockito.Mockito.when(mockSettings.getAppSettings()).thenReturn(appSettings);

        CbxMetadataWriter writer = new CbxMetadataWriter(mockSettings, new ArchiveService());

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
