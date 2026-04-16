package org.booklore.service.reader;

import com.github.benmanes.caffeine.cache.Cache;
import org.booklore.exception.ApiError;
import org.booklore.exception.APIException;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.ArchiveService;
import org.booklore.service.reader.ChapterCacheService;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbxReaderServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    ArchiveService archiveService;

    @Mock
    ChapterCacheService chapterCacheService;

    @InjectMocks
    CbxReaderService cbxReaderService;

    @Mock
    com.github.benmanes.caffeine.cache.Cache<String, java.util.zip.ZipFile> mockZipCache;

    @Captor
    ArgumentCaptor<Long> longCaptor;

    BookEntity bookEntity;
    Path cbzPath;

    @BeforeEach
    void setup() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        cbzPath = Path.of("/tmp/test.cbz");
        // Manually inject the mock cache
        cbxReaderService.setZipHandleCache(mockZipCache);
        // Ensure zipHandleCache is mocked to return null on any key access
        lenient().when(mockZipCache.get(anyString(), any())).thenReturn(null);
    }

    @Test
    void testGetAvailablePages_ThrowsOnMissingBook() {
        when(bookRepository.findByIdForStreaming(2L)).thenReturn(Optional.empty());
        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(), () -> cbxReaderService.getAvailablePages(2L));
    }

    @Test
    void testGetAvailablePages_Success() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        when(archiveService.streamEntryNames(cbzPath)).then((i) -> Stream.of("1.jpg"));

        try (
            MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class);
            MockedStatic<Files> filesStatic = mockStatic(Files.class);
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath);
            filesStatic.when(() -> Files.getLastModifiedTime(cbzPath)).thenReturn(FileTime.from(Instant.now()));

            cbxReaderService.initCache(1L, null);

            List<Integer> pages = cbxReaderService.getAvailablePages(1L);
            assertEquals(List.of(1), pages);
        }
    }

    @Test
    void testStreamPageImage_Success() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        when(archiveService.streamEntryNames(cbzPath)).then((i) -> Stream.of("1.jpg"));
        when(
            archiveService.transferEntryTo(eq(cbzPath), eq("1.jpg"), any())
        ).then((i) -> {i.getArgument(2, OutputStream.class).write(new byte[]{1, 2, 3}); return null; });
        try (
            MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class);
            MockedStatic<Files> filesStatic = mockStatic(Files.class);
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath);
            filesStatic.when(() -> Files.getLastModifiedTime(cbzPath)).thenReturn(FileTime.from(Instant.now()));

            cbxReaderService.initCache(1L, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cbxReaderService.streamPageImage(1L, 1, out);
            assertArrayEquals(new byte[]{1, 2, 3}, out.toByteArray());
        }
    }

    @Test
    void testStreamPageImage_PageOutOfRange_Throws() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        when(archiveService.streamEntryNames(cbzPath)).then((i) -> Stream.of("1.jpg"));
        try (
            MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class);
            MockedStatic<Files> filesStatic = mockStatic(Files.class);
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath);
            filesStatic.when(() -> Files.getLastModifiedTime(cbzPath)).thenReturn(FileTime.from(Instant.now()));

            cbxReaderService.initCache(1L, null);

            assertThrows(
                    FileNotFoundException.class,
                    () -> cbxReaderService.streamPageImage(1L, 2, new ByteArrayOutputStream())
            );
        }
    }

    @Test
    void testStreamPageImage_EntryNotFound_Throws() throws Exception {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        when(archiveService.streamEntryNames(cbzPath)).then((i) -> Stream.of("1.jpg"));
        try (
            MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class);
            MockedStatic<Files> filesStatic = mockStatic(Files.class);
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath);
            filesStatic.when(() -> Files.getLastModifiedTime(cbzPath)).thenReturn(FileTime.from(Instant.now()));

            assertThrows(
                    FileNotFoundException.class,
                    () -> cbxReaderService.streamPageImage(1L, 2, new ByteArrayOutputStream())
            );
        }
    }

    @Test
    void testStreamPageImage_InvalidBookType_Throws() {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        APIException ex = assertThrows(APIException.class, () ->
                cbxReaderService.streamPageImage(1L, "../traversal", 1, new ByteArrayOutputStream())
        );
        assertTrue(ex.getMessage().contains("Invalid book type"), "Expected INVALID_INPUT, got: " + ex.getMessage());
    }

    @Test
    void testInitCache_InvalidBookType_Throws() {
        when(bookRepository.findByIdForStreaming(1L)).thenReturn(Optional.of(bookEntity));
        APIException ex = assertThrows(APIException.class, () ->
                cbxReaderService.initCache(1L, "../traversal")
        );
        assertTrue(ex.getMessage().contains("Invalid book type"), "Expected INVALID_INPUT, got: " + ex.getMessage());
    }
}
