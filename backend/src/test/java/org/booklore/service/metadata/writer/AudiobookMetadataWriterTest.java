package org.booklore.service.metadata.writer;

import org.booklore.model.dto.settings.AppSettings;
import org.booklore.model.dto.settings.MetadataPersistenceSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.appsettings.AppSettingService;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudiobookMetadataWriterTest {

    @TempDir
    Path tempDir;

    @Mock
    AppSettingService appSettingService;

    private AudiobookMetadataWriter writer;

    @BeforeEach
    void setUp() {
        MetadataPersistenceSettings.FormatSettings audiobookSettings =
                new MetadataPersistenceSettings.FormatSettings(true, 100);
        MetadataPersistenceSettings.SaveToOriginalFile saveToOriginalFile =
                new MetadataPersistenceSettings.SaveToOriginalFile();
        saveToOriginalFile.setAudiobook(audiobookSettings);
        MetadataPersistenceSettings persistenceSettings = new MetadataPersistenceSettings();
        persistenceSettings.setSaveToOriginalFile(saveToOriginalFile);
        AppSettings settings = new AppSettings();
        settings.setMetadataPersistenceSettings(persistenceSettings);
        when(appSettingService.getAppSettings()).thenReturn(settings);

        writer = new AudiobookMetadataWriter(appSettingService);
    }

    @Test
    void restoresBackupWhenWrittenAudiobookFailsValidation() throws Exception {
        Path path = tempDir.resolve("audiobook.m4b");
        Files.writeString(path, "original audio");
        File file = path.toFile();

        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        when(audioFile.getTagOrCreateAndSetDefault()).thenReturn(tag);
        when(tag.getFirst(any(FieldKey.class))).thenReturn("");
        doAnswer(_ -> {
            Files.writeString(path, "corrupt output");
            return null;
        }).when(audioFile).commit();

        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Updated title");

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(file))
                    .thenReturn(audioFile)
                    .thenThrow(new CannotReadException("missing audio track"));

            writer.saveMetadataToFile(file, metadata, null, null);
        }

        assertThat(path).hasContent("original audio");
        assertThat(path.resolveSibling("audiobook.m4b.bak")).doesNotExist();
        verify(audioFile).commit();
    }

    @Test
    void restoresBackupWhenCoverWriteFailsValidation() throws Exception {
        Path path = tempDir.resolve("cover-update.m4b");
        Files.writeString(path, "original audio");
        File file = path.toFile();

        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        when(audioFile.getTagOrCreateAndSetDefault()).thenReturn(tag);
        doAnswer(_ -> {
            Files.writeString(path, "corrupt output");
            return null;
        }).when(audioFile).commit();

        BookFileEntity bookFile = mock(BookFileEntity.class);
        when(bookFile.getBookType()).thenReturn(BookFileType.AUDIOBOOK);
        when(bookFile.getFullFilePath()).thenReturn(path);
        BookEntity book = mock(BookEntity.class);
        when(book.getBookFiles()).thenReturn(Set.of(bookFile));

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(file))
                    .thenReturn(audioFile)
                    .thenThrow(new CannotReadException("missing audio track"));

            writer.replaceCoverImageFromBytes(book, new byte[]{1, 2, 3, 4});
        }

        assertThat(path).hasContent("original audio");
        assertThat(path.resolveSibling("cover-update.m4b.bak")).doesNotExist();
        verify(audioFile).commit();
    }
}
