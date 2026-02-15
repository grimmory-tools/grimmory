package org.booklore.service.reader;

import org.booklore.model.dto.response.AudiobookChapter;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.model.dto.response.AudiobookTrack;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudioMetadataServiceTest {

    @Mock
    AudioFileUtilityService audioFileUtility;

    @InjectMocks
    AudioMetadataService audioMetadataService;

    @TempDir
    Path tempDir;

    BookEntity bookEntity;
    BookFileEntity bookFileEntity;
    Path audioPath;

    @BeforeEach
    void setUp() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);

        bookFileEntity = new BookFileEntity();
        bookFileEntity.setId(10L);
        bookFileEntity.setBook(bookEntity);
        bookFileEntity.setFolderBased(false);

        audioPath = tempDir.resolve("audiobook.m4b");
        Files.createFile(audioPath);
    }

    @Test
    void getMetadata_singleFile_extractsBasicInfo() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(tag);
        when(header.getPreciseTrackLength()).thenReturn(3600.0);
        when(header.getBitRateAsNumber()).thenReturn(128L);
        when(header.getEncodingType()).thenReturn("AAC");
        when(header.getSampleRateAsNumber()).thenReturn(44100);
        when(header.getChannels()).thenReturn("Stereo");

        when(tag.getFirst(FieldKey.TITLE)).thenReturn("Test Audiobook");
        when(tag.getFirst(FieldKey.ARTIST)).thenReturn("Test Author");
        when(tag.getFirst(FieldKey.COMPOSER)).thenReturn("Test Narrator");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);

            assertNotNull(info);
            assertEquals(1L, info.getBookId());
            assertEquals(10L, info.getBookFileId());
            assertFalse(info.isFolderBased());
            assertEquals(3600000L, info.getDurationMs());
            assertEquals(128, info.getBitrate());
            assertEquals("AAC", info.getCodec());
            assertEquals(44100, info.getSampleRate());
            assertEquals(2, info.getChannels());
            assertEquals("Test Audiobook", info.getTitle());
            assertEquals("Test Author", info.getAuthor());
            assertEquals("Test Narrator", info.getNarrator());
        }
    }

    @Test
    void getMetadata_singleFile_handlesNullTag() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(1800.0);
        when(header.getBitRateAsNumber()).thenReturn(256L);
        when(header.getEncodingType()).thenReturn("MP3");
        when(header.getSampleRateAsNumber()).thenReturn(48000);
        when(header.getChannels()).thenReturn("Mono");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);

            assertNotNull(info);
            assertEquals(1800000L, info.getDurationMs());
            assertEquals(1, info.getChannels());
            assertNull(info.getTitle());
            assertNull(info.getAuthor());
            assertNull(info.getNarrator());
        }
    }

    @Test
    void getMetadata_singleFile_createsDefaultChapterWhenNoChapters() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(7200.0);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);

            assertNotNull(info.getChapters());
            assertEquals(1, info.getChapters().size());
            AudiobookChapter chapter = info.getChapters().get(0);
            assertEquals(0, chapter.getIndex());
            assertEquals("Full Audiobook", chapter.getTitle());
            assertEquals(0L, chapter.getStartTimeMs());
            assertEquals(7200000L, chapter.getEndTimeMs());
            assertEquals(7200000L, chapter.getDurationMs());
        }
    }

    @Test
    void getMetadata_usesDbMetadataWhenAvailable() throws Exception {
        bookFileEntity.setDurationSeconds(3600L);
        bookFileEntity.setBitrate(192);
        bookFileEntity.setSampleRate(48000);
        bookFileEntity.setChannels(2);
        bookFileEntity.setCodec("MP3");
        bookFileEntity.setChapterCount(5);
        bookFileEntity.setChapters(List.of(
                BookFileEntity.AudioFileChapter.builder()
                        .index(0)
                        .title("Chapter 1")
                        .startTimeMs(0L)
                        .endTimeMs(1000000L)
                        .durationMs(1000000L)
                        .build()
        ));

        var bookMetadata = new BookMetadataEntity();
        bookMetadata.setTitle("DB Book Title");
        bookMetadata.setNarrator("DB Narrator");
        bookEntity.setMetadata(bookMetadata);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);

            assertNotNull(info);
            assertEquals("DB Narrator", info.getNarrator());
            assertEquals(3600000L, info.getDurationMs());
            assertEquals(192, info.getBitrate());
            assertEquals("MP3", info.getCodec());
            assertEquals("DB Book Title", info.getTitle());
            assertEquals(1, info.getChapters().size());
            assertEquals("Chapter 1", info.getChapters().get(0).getTitle());

            audioFileIOMock.verify(() -> AudioFileIO.read(any()), never());
        }
    }

    @Test
    void getMetadata_folderBased_extractsTracksInfo() throws Exception {
        bookFileEntity.setFolderBased(true);
        Path folderPath = tempDir.resolve("audiobook_folder");
        Files.createDirectory(folderPath);

        Path track1 = folderPath.resolve("01 - Chapter One.mp3");
        Path track2 = folderPath.resolve("02 - Chapter Two.mp3");
        Files.createFile(track1);
        Files.createFile(track2);

        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1, track2));
        when(audioFileUtility.getTrackTitleFromFilename("01 - Chapter One.mp3")).thenReturn("01 - Chapter One");
        when(audioFileUtility.getTrackTitleFromFilename("02 - Chapter Two.mp3")).thenReturn("02 - Chapter Two");
        when(audioFileUtility.isAudioFile(any())).thenReturn(true);

        AudioFile audioFile1 = mock(AudioFile.class);
        AudioFile audioFile2 = mock(AudioFile.class);
        AudioHeader header1 = mock(AudioHeader.class);
        AudioHeader header2 = mock(AudioHeader.class);
        Tag tag1 = mock(Tag.class);

        when(audioFile1.getAudioHeader()).thenReturn(header1);
        when(audioFile1.getTag()).thenReturn(tag1);
        when(header1.getPreciseTrackLength()).thenReturn(1800.0);
        when(header1.getBitRateAsNumber()).thenReturn(192L);
        when(header1.getEncodingType()).thenReturn("MP3");
        when(header1.getSampleRateAsNumber()).thenReturn(44100);
        when(header1.getChannels()).thenReturn("Stereo");
        when(tag1.getFirst(FieldKey.ALBUM)).thenReturn("Test Album");
        when(tag1.getFirst(FieldKey.ALBUM_ARTIST)).thenReturn("Test Author");
        when(tag1.getFirst(FieldKey.TITLE)).thenReturn("Chapter One");

        when(audioFile2.getAudioHeader()).thenReturn(header2);
        when(audioFile2.getTag()).thenReturn(null);
        when(header2.getPreciseTrackLength()).thenReturn(2400.0);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(track1.toFile())).thenReturn(audioFile1);
            audioFileIOMock.when(() -> AudioFileIO.read(track2.toFile())).thenReturn(audioFile2);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, folderPath);

            assertNotNull(info);
            assertTrue(info.isFolderBased());
            assertEquals(4200000L, info.getDurationMs());
            assertEquals("Test Album", info.getTitle());
            assertEquals("Test Author", info.getAuthor());

            List<AudiobookTrack> tracks = info.getTracks();
            assertNotNull(tracks);
            assertEquals(2, tracks.size());

            assertEquals(0, tracks.get(0).getIndex());
            assertEquals("Chapter One", tracks.get(0).getTitle());
            assertEquals(1800000L, tracks.get(0).getDurationMs());
            assertEquals(0L, tracks.get(0).getCumulativeStartMs());

            assertEquals(1, tracks.get(1).getIndex());
            assertEquals(2400000L, tracks.get(1).getDurationMs());
            assertEquals(1800000L, tracks.get(1).getCumulativeStartMs());
        }
    }

    @Test
    void getMetadata_folderBased_throwsExceptionForEmptyFolder() throws Exception {
        bookFileEntity.setFolderBased(true);
        Path folderPath = tempDir.resolve("empty_folder");
        Files.createDirectory(folderPath);

        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of());
        when(audioFileUtility.isAudioFile(any())).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> audioMetadataService.getMetadata(bookFileEntity, folderPath));
    }

    @Test
    void getMetadata_folderBased_usesFallbackTitleFromFilename() throws Exception {
        bookFileEntity.setFolderBased(true);
        Path folderPath = tempDir.resolve("audiobook_folder2");
        Files.createDirectory(folderPath);

        Path track1 = folderPath.resolve("track.mp3");
        Files.createFile(track1);

        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1));
        when(audioFileUtility.getTrackTitleFromFilename("track.mp3")).thenReturn("track");
        when(audioFileUtility.isAudioFile(any())).thenReturn(true);

        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);
        Tag tag = mock(Tag.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(tag);
        when(header.getPreciseTrackLength()).thenReturn(600.0);
        when(tag.getFirst(FieldKey.TITLE)).thenReturn("");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(track1.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, folderPath);

            assertEquals("track", info.getTracks().get(0).getTitle());
        }
    }

    @Test
    void getEmbeddedCoverArt_returnsArtworkData() throws Exception {
        byte[] expectedData = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x01};

        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(artwork);
        when(artwork.getBinaryData()).thenReturn(expectedData);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            byte[] result = audioMetadataService.getEmbeddedCoverArt(audioPath);

            assertArrayEquals(expectedData, result);
        }
    }

    @Test
    void getEmbeddedCoverArt_returnsNullWhenNoArtwork() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(null);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            byte[] result = audioMetadataService.getEmbeddedCoverArt(audioPath);

            assertNull(result);
        }
    }

    @Test
    void getEmbeddedCoverArt_returnsNullWhenNoTag() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        when(audioFile.getTag()).thenReturn(null);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            byte[] result = audioMetadataService.getEmbeddedCoverArt(audioPath);

            assertNull(result);
        }
    }

    @Test
    void getEmbeddedCoverArt_returnsNullOnException() {
        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenThrow(new RuntimeException("Read error"));

            byte[] result = audioMetadataService.getEmbeddedCoverArt(audioPath);

            assertNull(result);
        }
    }

    @Test
    void getCoverArtMimeType_returnsMimeTypeFromArtwork() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(artwork);
        when(artwork.getMimeType()).thenReturn("image/png");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            String result = audioMetadataService.getCoverArtMimeType(audioPath);

            assertEquals("image/png", result);
        }
    }

    @Test
    void getCoverArtMimeType_detectsJpegFromMagicBytes() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(artwork);
        when(artwork.getMimeType()).thenReturn(null);
        when(artwork.getBinaryData()).thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00});

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            String result = audioMetadataService.getCoverArtMimeType(audioPath);

            assertEquals("image/jpeg", result);
        }
    }

    @Test
    void getCoverArtMimeType_detectsPngFromMagicBytes() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(artwork);
        when(artwork.getMimeType()).thenReturn("");
        when(artwork.getBinaryData()).thenReturn(new byte[]{(byte) 0x89, (byte) 0x50, 0x4E, 0x47});

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            String result = audioMetadataService.getCoverArtMimeType(audioPath);

            assertEquals("image/png", result);
        }
    }

    @Test
    void getCoverArtMimeType_returnsDefaultJpegWhenCannotDetermine() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        Tag tag = mock(Tag.class);
        Artwork artwork = mock(Artwork.class);

        when(audioFile.getTag()).thenReturn(tag);
        when(tag.getFirstArtwork()).thenReturn(artwork);
        when(artwork.getMimeType()).thenReturn(null);
        when(artwork.getBinaryData()).thenReturn(new byte[]{0x00, 0x01});

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            String result = audioMetadataService.getCoverArtMimeType(audioPath);

            assertEquals("image/jpeg", result);
        }
    }

    @Test
    void getCoverArtMimeType_returnsJpegOnException() {
        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenThrow(new RuntimeException("Error"));

            String result = audioMetadataService.getCoverArtMimeType(audioPath);

            assertEquals("image/jpeg", result);
        }
    }

    @Test
    void getMetadata_parsesStereoChannels() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getChannels()).thenReturn("Stereo");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertEquals(2, info.getChannels());
        }
    }

    @Test
    void getMetadata_parsesMonoChannels() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getChannels()).thenReturn("Mono");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertEquals(1, info.getChannels());
        }
    }

    @Test
    void getMetadata_parsesNumericChannels() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getChannels()).thenReturn("6 channels");

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertEquals(6, info.getChannels());
        }
    }

    @Test
    void getMetadata_handlesNullChannels() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getChannels()).thenReturn(null);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertNull(info.getChannels());
        }
    }

    @Test
    void getMetadata_handlesZeroBitrate() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getBitRateAsNumber()).thenReturn(0L);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertNull(info.getBitrate());
        }
    }

    @Test
    void getMetadata_handlesNegativeBitrate() throws Exception {
        AudioFile audioFile = mock(AudioFile.class);
        AudioHeader header = mock(AudioHeader.class);

        when(audioFile.getAudioHeader()).thenReturn(header);
        when(audioFile.getTag()).thenReturn(null);
        when(header.getPreciseTrackLength()).thenReturn(100.0);
        when(header.getBitRateAsNumber()).thenReturn(-1L);

        try (MockedStatic<AudioFileIO> audioFileIOMock = mockStatic(AudioFileIO.class)) {
            audioFileIOMock.when(() -> AudioFileIO.read(audioPath.toFile())).thenReturn(audioFile);

            AudiobookInfo info = audioMetadataService.getMetadata(bookFileEntity, audioPath);
            assertNull(info.getBitrate());
        }
    }
}
