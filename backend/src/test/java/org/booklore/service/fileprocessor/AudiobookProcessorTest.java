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
import tools.jackson.databind.ObjectMapper;

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
        Files.createFile(audiobookFolder.resolve("01.mp3"));
        Path secondTrack = Files.createFile(audiobookFolder.resolve("02.mp3"));

        AudioHeader secondHeader = mock(AudioHeader.class);
        when(secondHeader.getPreciseTrackLength()).thenReturn(24_540.0);
        AudioFile secondAudioFile = mock(AudioFile.class);
        when(secondAudioFile.getAudioHeader()).thenReturn(secondHeader);

        AudiobookMetadataExtractor extractor = new AudiobookMetadataExtractor(
                new ObjectMapper(), mock(FfprobeService.class));
        AudiobookProcessor processor = new AudiobookProcessor(
                mock(BookRepository.class),
                mock(BookAdditionalFileRepository.class),
                mock(BookCreatorService.class),
                mock(BookMapper.class),
                mock(FileService.class),
                mock(MetadataMatchService.class),
                mock(SidecarMetadataWriter.class),
                extractor);

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

        BookMetadata firstTrackMetadata = BookMetadata.builder()
                .audiobookMetadata(AudiobookMetadata.builder()
                        .durationSeconds(26_760L)
                        .bitrate(128)
                        .codec("MP3")
                        .build())
                .build();

        try (MockedStatic<AudioFileIO> audioFileIO = mockStatic(AudioFileIO.class)) {
            audioFileIO.when(() -> AudioFileIO.read(secondTrack.toFile())).thenReturn(secondAudioFile);

            processor.setAudiobookTechnicalMetadata(book, firstTrackMetadata);

            audioFileIO.verify(() -> AudioFileIO.read(secondTrack.toFile()));
        }

        assertThat(audiobookFile.getDurationSeconds()).isEqualTo(51_300L);
        assertThat(audiobookFile.getBitrate()).isEqualTo(128);
        assertThat(audiobookFile.getCodec()).isEqualTo("MP3");
    }
}
