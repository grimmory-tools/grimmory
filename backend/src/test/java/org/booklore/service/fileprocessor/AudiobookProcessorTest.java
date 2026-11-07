package org.booklore.service.fileprocessor;

import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.AudiobookMetadata;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.metadata.extractor.AudiobookMetadataExtractor;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.reader.FfprobeService;
import org.booklore.util.FileService;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AudiobookProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void setAudiobookTechnicalMetadata_sumsAllFolderTrackDurations() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("Example Book"));
        Path firstTrack = Files.createFile(audiobookFolder.resolve("01.mp3"));
        Path secondTrack = Files.createFile(audiobookFolder.resolve("02.mp3"));

        BookEntity book = createFolderBasedAudiobook(audiobookFolder);
        BookFileEntity audiobookFile = book.getPrimaryBookFile();
        AudioFile firstAudioFile = mockAudioFile(26_760.0);
        AudioFile secondAudioFile = mockAudioFile(24_540.0);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(firstTrack.toFile())).thenReturn(firstAudioFile);
            audioFileIO.when(() -> AudioFileIO.read(secondTrack.toFile())).thenReturn(secondAudioFile);

            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(26_760L));

            audioFileIO.verify(() -> AudioFileIO.read(firstTrack.toFile()));
            audioFileIO.verify(() -> AudioFileIO.read(secondTrack.toFile()));
        }

        assertThat(audiobookFile.getDurationSeconds()).isEqualTo(51_300L);
        assertThat(audiobookFile.getBitrate()).isEqualTo(128);
        assertThat(audiobookFile.getCodec()).isEqualTo("MP3");
    }

    @Test
    void setAudiobookTechnicalMetadata_sumsMillisecondsBeforeRoundingToSeconds() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("Precise Duration"));
        Path firstTrack = Files.createFile(audiobookFolder.resolve("01.mp3"));
        Path secondTrack = Files.createFile(audiobookFolder.resolve("02.mp3"));
        BookEntity book = createFolderBasedAudiobook(audiobookFolder);
        AudioFile firstAudioFile = mockAudioFile(10.75);
        AudioFile secondAudioFile = mockAudioFile(20.75);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(firstTrack.toFile())).thenReturn(firstAudioFile);
            audioFileIO.when(() -> AudioFileIO.read(secondTrack.toFile())).thenReturn(secondAudioFile);

            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(11L));
        }

        assertThat(book.getPrimaryBookFile().getDurationSeconds()).isEqualTo(32L);
    }

    @Test
    void setAudiobookTechnicalMetadata_extractsFirstTrackWhenDurationIsMissing() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("Missing Duration"));
        Path firstTrack = Files.createFile(audiobookFolder.resolve("01.mp3"));
        Path secondTrack = Files.createFile(audiobookFolder.resolve("02.mp3"));
        BookEntity book = createFolderBasedAudiobook(audiobookFolder);
        AudioFile firstAudioFile = mockAudioFile(120.0);
        AudioFile secondAudioFile = mockAudioFile(180.0);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(firstTrack.toFile())).thenReturn(firstAudioFile);
            audioFileIO.when(() -> AudioFileIO.read(secondTrack.toFile())).thenReturn(secondAudioFile);

            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(null));

            audioFileIO.verify(() -> AudioFileIO.read(firstTrack.toFile()));
            audioFileIO.verify(() -> AudioFileIO.read(secondTrack.toFile()));
        }

        assertThat(book.getPrimaryBookFile().getDurationSeconds()).isEqualTo(300L);
    }

    @Test
    void setAudiobookTechnicalMetadata_preservesDurationForEmptyFolder() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("Empty Folder"));
        BookEntity book = createFolderBasedAudiobook(audiobookFolder);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(600L));

            audioFileIO.verifyNoInteractions();
        }

        assertThat(book.getPrimaryBookFile().getDurationSeconds()).isEqualTo(600L);
    }

    @Test
    void setAudiobookTechnicalMetadata_skipsInvalidLaterTracks() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("Invalid Tracks"));
        Path firstTrack = Files.createFile(audiobookFolder.resolve("01.mp3"));
        Path zeroDurationTrack = Files.createFile(audiobookFolder.resolve("02.mp3"));
        Path unreadableTrack = Files.createFile(audiobookFolder.resolve("03.mp3"));
        BookEntity book = createFolderBasedAudiobook(audiobookFolder);
        AudioFile firstAudioFile = mockAudioFile(240.0);
        AudioFile zeroDurationAudioFile = mockAudioFile(0.0);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(firstTrack.toFile())).thenReturn(firstAudioFile);
            audioFileIO.when(() -> AudioFileIO.read(zeroDurationTrack.toFile())).thenReturn(zeroDurationAudioFile);
            audioFileIO.when(() -> AudioFileIO.read(unreadableTrack.toFile()))
                    .thenThrow(new RuntimeException("unreadable"));

            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(240L));

            audioFileIO.verify(() -> AudioFileIO.read(zeroDurationTrack.toFile()));
            audioFileIO.verify(() -> AudioFileIO.read(unreadableTrack.toFile()));
        }

        assertThat(book.getPrimaryBookFile().getDurationSeconds()).isEqualTo(240L);
    }

    @Test
    void setAudiobookTechnicalMetadata_leavesDurationNullWhenNoDurationIsAvailable() throws Exception {
        Path audiobookFolder = Files.createDirectory(tempDir.resolve("No Durations"));
        Path firstTrack = Files.createFile(audiobookFolder.resolve("01.mp3"));
        BookEntity book = createFolderBasedAudiobook(audiobookFolder);
        AudioFile zeroDurationAudioFile = mockAudioFile(0.0);

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(firstTrack.toFile())).thenReturn(zeroDurationAudioFile);

            createProcessor().setAudiobookTechnicalMetadata(book, createMetadata(null));

            audioFileIO.verify(() -> AudioFileIO.read(firstTrack.toFile()));
        }

        assertThat(book.getPrimaryBookFile().getDurationSeconds()).isNull();
    }

    private AudiobookProcessor createProcessor() {
        AudiobookMetadataExtractor extractor = new AudiobookMetadataExtractor(
                JsonMapper.shared(), mock(FfprobeService.class));
        return new AudiobookProcessor(
                mock(BookRepository.class),
                mock(BookAdditionalFileRepository.class),
                mock(BookCreatorService.class),
                mock(BookMapper.class),
                mock(FileService.class),
                mock(MetadataMatchService.class),
                mock(SidecarMetadataWriter.class),
                extractor);
    }

    private BookEntity createFolderBasedAudiobook(Path audiobookFolder) {
        BookEntity book = new BookEntity();
        book.setLibraryPath(LibraryPathEntity.builder().path(tempDir.toString()).build());

        BookFileEntity audiobookFile = BookFileEntity.builder()
                .id(1L)
                .book(book)
                .fileName(audiobookFolder.getFileName().toString())
                .fileSubPath("")
                .isBookFormat(true)
                .folderBased(true)
                .bookType(BookFileType.AUDIOBOOK)
                .build();
        book.setBookFiles(Set.of(audiobookFile));
        return book;
    }

    private BookMetadata createMetadata(Long durationSeconds) {
        return BookMetadata.builder()
                .audiobookMetadata(AudiobookMetadata.builder()
                        .durationSeconds(durationSeconds)
                        .bitrate(128)
                        .codec("MP3")
                        .build())
                .build();
    }

    private AudioFile mockAudioFile(double durationSeconds) {
        AudioHeader header = mock(AudioHeader.class);
        when(header.getPreciseTrackLength()).thenReturn(durationSeconds);
        AudioFile audioFile = mock(AudioFile.class);
        when(audioFile.getAudioHeader()).thenReturn(header);
        return audioFile;
    }
}
