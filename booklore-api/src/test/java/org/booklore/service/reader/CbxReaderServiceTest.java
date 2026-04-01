package org.booklore.service.reader;

import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.ArchiveService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbxReaderServiceTest {

    @Mock
    BookRepository bookRepository;

    @Mock
    ArchiveService archiveService;

    @InjectMocks
    CbxReaderService cbxReaderService;

    @Captor
    ArgumentCaptor<Long> longCaptor;

    BookEntity bookEntity;
    Path cbzPath;

    @BeforeEach
    void setup() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        cbzPath = Path.of("/tmp/test.cbz");
    }

    @Test
    void testGetAvailablePages_ThrowsOnMissingBook() {
        when(bookRepository.findByIdWithBookFiles(2L)).thenReturn(Optional.empty());
        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(), () -> cbxReaderService.getAvailablePages(2L));
    }

    @Test
    void testGetAvailablePages_Success() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
        when(archiveService.streamEntryNames(cbzPath)).then((i) -> Stream.of("1.jpg"));

        try (
            MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class);
            MockedStatic<Files> filesStatic = mockStatic(Files.class);
        ) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath);
            filesStatic.when(() -> Files.getLastModifiedTime(cbzPath)).thenReturn(FileTime.from(Instant.now()));

            List<Integer> pages = cbxReaderService.getAvailablePages(1L);
            assertEquals(List.of(1), pages);
        }
    }

    @Test
    void testStreamPageImage_Success() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
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

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            cbxReaderService.streamPageImage(1L, 1, out);
            assertArrayEquals(new byte[]{1, 2, 3}, out.toByteArray());
        }
    }

    @Test
    void testStreamPageImage_PageOutOfRange_Throws() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
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
    void testStreamPageImage_EntryNotFound_Throws() throws Exception {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(bookEntity));
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
}
