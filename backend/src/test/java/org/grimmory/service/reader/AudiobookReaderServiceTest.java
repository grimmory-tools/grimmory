package org.grimmory.service.reader;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.response.AudiobookInfo;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.service.FileStreamingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AudiobookReaderServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    AudioMetadataService audioMetadataService;

    @Mock
    AudioFileUtilityService audioFileUtility;

    @Mock
    FileStreamingService fileStreamingService;

    @InjectMocks
    AudiobookReaderService audiobookReaderService;

    @TempDir
    Path tempDir;

    BookEntity bookEntity;
    BookFileEntity audiobookFileEntity;
    BookFileEntity folderAudiobookFileEntity;
    Path audioPath;
    Path folderPath;

    @BeforeEach
    void setUp() throws Exception {
        audioPath = tempDir.resolve("audiobook.m4b");
        Files.createFile(audioPath);

        folderPath = tempDir.resolve("audiobook_folder");
        Files.createDirectory(folderPath);

        bookEntity = mock(BookEntity.class);
        when(bookEntity.getId()).thenReturn(1L);

        audiobookFileEntity = mock(BookFileEntity.class);
        when(audiobookFileEntity.getId()).thenReturn(10L);
        when(audiobookFileEntity.getBook()).thenReturn(bookEntity);
        when(audiobookFileEntity.getBookType()).thenReturn(BookFileType.AUDIOBOOK);
        when(audiobookFileEntity.isFolderBased()).thenReturn(false);
        when(audiobookFileEntity.isBookFormat()).thenReturn(true);
        when(audiobookFileEntity.getFullFilePath()).thenReturn(audioPath);
        when(audiobookFileEntity.getFirstAudioFile()).thenReturn(audioPath);

        folderAudiobookFileEntity = mock(BookFileEntity.class);
        when(folderAudiobookFileEntity.getId()).thenReturn(20L);
        when(folderAudiobookFileEntity.getBook()).thenReturn(bookEntity);
        when(folderAudiobookFileEntity.getBookType()).thenReturn(BookFileType.AUDIOBOOK);
        when(folderAudiobookFileEntity.isFolderBased()).thenReturn(true);
        when(folderAudiobookFileEntity.isBookFormat()).thenReturn(true);
        when(folderAudiobookFileEntity.getFullFilePath()).thenReturn(folderPath);

        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(audiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);
    }

    // ==================== getAudiobookInfo tests ====================

    @Test
    void getAudiobookInfo_returnsInfoFromMetadataService() throws Exception {
        AudiobookInfo expectedInfo = AudiobookInfo.builder()
                .bookId(1L)
                .bookFileId(10L)
                .title("Test Audiobook")
                .author("Test Author")
                .durationMs(3600000L)
                .build();

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getMetadata(audiobookFileEntity, audioPath)).thenReturn(expectedInfo);

        AudiobookInfo result = audiobookReaderService.getAudiobookInfo(1L, null);

        assertEquals(expectedInfo, result);
        verify(audioMetadataService).getMetadata(audiobookFileEntity, audioPath);
    }

    @Test
    void getAudiobookInfo_withBookType_selectsCorrectFile() throws Exception {
        BookFileEntity epubFile = mock(BookFileEntity.class);
        when(epubFile.getId()).thenReturn(30L);
        when(epubFile.getBookType()).thenReturn(BookFileType.EPUB);

        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(audiobookFileEntity);
        bookFiles.add(epubFile);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        AudiobookInfo expectedInfo = AudiobookInfo.builder().bookId(1L).build();

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getMetadata(audiobookFileEntity, audioPath)).thenReturn(expectedInfo);

        AudiobookInfo result = audiobookReaderService.getAudiobookInfo(1L, "AUDIOBOOK");

        assertEquals(expectedInfo, result);
        verify(audioMetadataService).getMetadata(eq(audiobookFileEntity), any());
    }

    @Test
    void getAudiobookInfo_throwsExceptionWhenBookNotFound() {
        when(bookRepository.findByIdForAudiobook(999L)).thenReturn(Optional.empty());

        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(),
                () -> audiobookReaderService.getAudiobookInfo(999L, null));
    }

    @Test
    void getAudiobookInfo_throwsExceptionWhenNoAudiobookFile() {
        BookFileEntity epubFile = mock(BookFileEntity.class);
        when(epubFile.getBookType()).thenReturn(BookFileType.EPUB);

        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(epubFile);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));

        assertThrows(ApiError.FILE_NOT_FOUND.createException().getClass(),
                () -> audiobookReaderService.getAudiobookInfo(1L, null));
    }

    @Test
    void getAudiobookInfo_throwsExceptionWhenRequestedTypeNotFound() {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));

        assertThrows(ApiError.FILE_NOT_FOUND.createException().getClass(),
                () -> audiobookReaderService.getAudiobookInfo(1L, "EPUB"));
    }

    @Test
    void getAudiobookInfo_wrapsMetadataServiceException() throws Exception {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getMetadata(any(), any())).thenThrow(new RuntimeException("Parse error"));

        assertThrows(ApiError.FILE_READ_ERROR.createException().getClass(),
                () -> audiobookReaderService.getAudiobookInfo(1L, null));
    }

    // ==================== getAudioFilePath tests ====================

    @Test
    void getAudioFilePath_singleFile_returnsFilePath() {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));

        Path result = audiobookReaderService.getAudioFilePath(1L, null, null);

        assertEquals(audioPath, result);
    }

    @Test
    void getAudioFilePath_singleFile_ignoresTrackIndex() {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));

        Path result = audiobookReaderService.getAudioFilePath(1L, null, 5);

        assertEquals(audioPath, result);
    }

    @Test
    void getAudioFilePath_folderBased_returnsTrackPath() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Path track2 = folderPath.resolve("track2.mp3");
        Files.createFile(track1);
        Files.createFile(track2);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1, track2));

        Path result = audiobookReaderService.getAudioFilePath(1L, null, 1);

        assertEquals(track2, result);
    }

    @Test
    void getAudioFilePath_folderBased_defaultsToFirstTrack() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Files.createFile(track1);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1));

        Path result = audiobookReaderService.getAudioFilePath(1L, null, null);

        assertEquals(track1, result);
    }

    @Test
    void getAudioFilePath_folderBased_throwsExceptionForInvalidIndex() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Files.createFile(track1);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1));

        assertThrows(ApiError.FILE_NOT_FOUND.createException().getClass(),
                () -> audiobookReaderService.getAudioFilePath(1L, null, 10));
    }

    @Test
    void getAudioFilePath_folderBased_throwsExceptionForNegativeIndex() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Files.createFile(track1);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioFileUtility.listAudioFiles(folderPath)).thenReturn(List.of(track1));

        assertThrows(ApiError.FILE_NOT_FOUND.createException().getClass(),
                () -> audiobookReaderService.getAudioFilePath(1L, null, -1));
    }

    // ==================== streamWithRangeSupport tests ====================

    @Test
    void streamWithRangeSupport_delegatesToFileStreamingService() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(audioFileUtility.getContentType(audioPath)).thenReturn("audio/mp4");

        audiobookReaderService.streamWithRangeSupport(audioPath, request, response);

        verify(audioFileUtility).getContentType(audioPath);
        verify(fileStreamingService).streamWithRangeSupport(audioPath, "audio/mp4", request, response);
    }

    @Test
    void streamWithRangeSupport_usesCorrectContentType() throws IOException {
        Path mp3Path = tempDir.resolve("audio.mp3");
        Files.createFile(mp3Path);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(audioFileUtility.getContentType(mp3Path)).thenReturn("audio/mpeg");

        audiobookReaderService.streamWithRangeSupport(mp3Path, request, response);

        verify(fileStreamingService).streamWithRangeSupport(mp3Path, "audio/mpeg", request, response);
    }

    // ==================== getEmbeddedCoverArt tests ====================

    @Test
    void getEmbeddedCoverArt_delegatesToMetadataService() {
        byte[] expectedData = new byte[]{0x01, 0x02, 0x03};

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getEmbeddedCoverArt(audioPath)).thenReturn(expectedData);

        byte[] result = audiobookReaderService.getEmbeddedCoverArt(1L, null);

        assertArrayEquals(expectedData, result);
        verify(audioMetadataService).getEmbeddedCoverArt(audioPath);
    }

    @Test
    void getEmbeddedCoverArt_folderBased_usesFirstAudioFile() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Files.createFile(track1);
        when(folderAudiobookFileEntity.getFirstAudioFile()).thenReturn(track1);

        byte[] expectedData = new byte[]{0x04, 0x05};

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getEmbeddedCoverArt(track1)).thenReturn(expectedData);

        byte[] result = audiobookReaderService.getEmbeddedCoverArt(1L, null);

        assertArrayEquals(expectedData, result);
        verify(audioMetadataService).getEmbeddedCoverArt(track1);
    }

    @Test
    void getEmbeddedCoverArt_returnsNullWhenNoCoverArt() {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getEmbeddedCoverArt(audioPath)).thenReturn(null);

        byte[] result = audiobookReaderService.getEmbeddedCoverArt(1L, null);

        assertNull(result);
    }

    // ==================== getCoverArtMimeType tests ====================

    @Test
    void getCoverArtMimeType_delegatesToMetadataService() {
        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getCoverArtMimeType(audioPath)).thenReturn("image/png");

        String result = audiobookReaderService.getCoverArtMimeType(1L, null);

        assertEquals("image/png", result);
        verify(audioMetadataService).getCoverArtMimeType(audioPath);
    }

    @Test
    void getCoverArtMimeType_folderBased_usesFirstAudioFile() throws IOException {
        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(folderAudiobookFileEntity);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        Path track1 = folderPath.resolve("track1.mp3");
        Files.createFile(track1);
        when(folderAudiobookFileEntity.getFirstAudioFile()).thenReturn(track1);

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getCoverArtMimeType(track1)).thenReturn("image/jpeg");

        String result = audiobookReaderService.getCoverArtMimeType(1L, null);

        assertEquals("image/jpeg", result);
    }

    // ==================== getContentType tests ====================

    @Test
    void getContentType_delegatesToAudioFileUtility() {
        when(audioFileUtility.getContentType(audioPath)).thenReturn("audio/mp4");

        String result = audiobookReaderService.getContentType(audioPath);

        assertEquals("audio/mp4", result);
        verify(audioFileUtility).getContentType(audioPath);
    }

    // ==================== Book file selection tests ====================

    @Test
    void getAudiobookInfo_selectsAudiobookWithBookFormat() throws Exception {
        BookFileEntity audiobookWithoutBookFormat = mock(BookFileEntity.class);
        when(audiobookWithoutBookFormat.getId()).thenReturn(40L);
        when(audiobookWithoutBookFormat.getBookType()).thenReturn(BookFileType.AUDIOBOOK);
        when(audiobookWithoutBookFormat.isBookFormat()).thenReturn(false);

        List<BookFileEntity> bookFiles = new ArrayList<>();
        bookFiles.add(audiobookFileEntity);
        bookFiles.add(audiobookWithoutBookFormat);
        when(bookEntity.getBookFiles()).thenReturn(bookFiles);

        AudiobookInfo expectedInfo = AudiobookInfo.builder().bookId(1L).build();

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getMetadata(eq(audiobookFileEntity), any())).thenReturn(expectedInfo);

        AudiobookInfo result = audiobookReaderService.getAudiobookInfo(1L, null);

        assertEquals(expectedInfo, result);
        verify(audioMetadataService).getMetadata(eq(audiobookFileEntity), any());
    }

    @Test
    void getAudiobookInfo_bookTypeIsCaseInsensitive() throws Exception {
        AudiobookInfo expectedInfo = AudiobookInfo.builder().bookId(1L).build();

        when(bookRepository.findByIdForAudiobook(1L)).thenReturn(Optional.of(bookEntity));
        when(audioMetadataService.getMetadata(any(), any())).thenReturn(expectedInfo);

        // Test lowercase
        audiobookReaderService.getAudiobookInfo(1L, "audiobook");
        verify(audioMetadataService, times(1)).getMetadata(any(), any());

        // Test mixed case
        audiobookReaderService.getAudiobookInfo(1L, "AudioBook");
        verify(audioMetadataService, times(2)).getMetadata(any(), any());
    }
}
