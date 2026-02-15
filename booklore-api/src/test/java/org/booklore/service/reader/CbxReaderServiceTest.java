package org.booklore.service.reader;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CbxReaderServiceTest {

    @Mock
    BookRepository bookRepository;

    @InjectMocks
    CbxReaderService cbxReaderService;

    @Captor
    ArgumentCaptor<Long> longCaptor;

    BookEntity bookEntity;
    Path cbzPath;
    Path cb7Path;
    Path cbrPath;

    @BeforeEach
    void setup() throws Exception {
        bookEntity = new BookEntity();
        bookEntity.setId(1L);
        cbzPath = Path.of("/tmp/test.cbz");
        cb7Path = Path.of("/tmp/test.cb7");
        cbrPath = Path.of("/tmp/test.cbr");
        Files.deleteIfExists(cbzPath);
        Files.deleteIfExists(cb7Path);
        Files.deleteIfExists(cbrPath);
    }

    @Test
    void testGetAvailablePages_CBZ_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath.toString());

            ZipArchiveEntry entry1 = new ZipArchiveEntry("1.jpg");
            ZipArchiveEntry entry2 = new ZipArchiveEntry("2.png");
            Enumeration<ZipArchiveEntry> entries = Collections.enumeration(List.of(entry1, entry2));
            ZipFile zipFile = mock(ZipFile.class);
            when(zipFile.getEntries()).thenReturn(entries);

            ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(cbzPath)).thenReturn(builder);
            when(builder.setCharset(any(Charset.class))).thenReturn(builder);
            when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
            when(builder.setIgnoreLocalFileHeader(anyBoolean())).thenReturn(builder);
            when(builder.get()).thenReturn(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(cbzPath);
                Files.setLastModifiedTime(cbzPath, FileTime.fromMillis(System.currentTimeMillis()));

                List<Integer> pages = cbxReaderService.getAvailablePages(1L);
                assertEquals(List.of(1, 2), pages);
            }
        }
    }

    @Test
    void testGetAvailablePages_CBZ_Fallback_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath.toString());

            // Unicode enabled -> throws Exception
            ZipFile zipFileFail = mock(ZipFile.class);
            when(zipFileFail.getEntries()).thenThrow(new IllegalArgumentException("Corrupt extra fields"));

            // Unicode disabled -> returns valid entries
            ZipArchiveEntry entry1 = new ZipArchiveEntry("1.jpg");
            Enumeration<ZipArchiveEntry> entries = Collections.enumeration(List.of(entry1));
            ZipFile zipFileSuccess = mock(ZipFile.class);
            when(zipFileSuccess.getEntries()).thenReturn(entries);

            ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(any(Path.class))).thenReturn(builder);
            when(builder.setCharset(any(Charset.class))).thenReturn(builder);
            when(builder.setIgnoreLocalFileHeader(anyBoolean())).thenReturn(builder);
            
            // Mock builder behavior based on UseUnicodeExtraFields call
            when(builder.setUseUnicodeExtraFields(anyBoolean())).thenAnswer(invocation -> {
                return builder;
            });
            

            when(builder.get())
                .thenReturn(zipFileFail) // 1. Fast, Unicode=True
                .thenReturn(zipFileFail) // 2. Slow, Unicode=True
                .thenReturn(zipFileSuccess); // 3. Fast, Unicode=False

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(cbzPath);
                Files.setLastModifiedTime(cbzPath, FileTime.fromMillis(System.currentTimeMillis()));

                List<Integer> pages = cbxReaderService.getAvailablePages(1L);
                assertEquals(List.of(1), pages);
                
                // Verify that we eventually called with useUnicode=false
                verify(builder).setUseUnicodeExtraFields(eq(false));
            }
        }
    }

    @Test
    void testStreamPageImage_CBZ_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath.toString());

            ZipArchiveEntry entry1 = new ZipArchiveEntry("1.jpg");
            Enumeration<ZipArchiveEntry> entries = Collections.enumeration(List.of(entry1));
            ZipFile zipFile = mock(ZipFile.class);
            when(zipFile.getEntries()).thenReturn(entries);
            when(zipFile.getEntry("1.jpg")).thenReturn(entry1);
            when(zipFile.getInputStream(entry1)).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

            ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(cbzPath)).thenReturn(builder);
            when(builder.setCharset(any(Charset.class))).thenReturn(builder);
            when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
            when(builder.setIgnoreLocalFileHeader(anyBoolean())).thenReturn(builder);
            when(builder.get()).thenReturn(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(cbzPath);
                Files.setLastModifiedTime(cbzPath, FileTime.fromMillis(System.currentTimeMillis()));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                cbxReaderService.streamPageImage(1L, 1, out);
                assertArrayEquals(new byte[]{1, 2, 3}, out.toByteArray());
            }
        }
    }

    @Test
    void testGetAvailablePages_CBZ_ThrowsOnMissingBook() {
        when(bookRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ApiError.BOOK_NOT_FOUND.createException().getClass(), () -> cbxReaderService.getAvailablePages(2L));
    }

    @Test
    void testGetAvailablePages_CB7_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cb7Path.toString());

            SevenZArchiveEntry entry1 = mock(SevenZArchiveEntry.class);
            when(entry1.getName()).thenReturn("1.jpg");
            when(entry1.isDirectory()).thenReturn(false);

            SevenZFile sevenZFile = mock(SevenZFile.class);
            when(sevenZFile.getNextEntry()).thenReturn(entry1, (SevenZArchiveEntry) null);

            SevenZFile.Builder builder = mock(SevenZFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(cb7Path)).thenReturn(builder);
            when(builder.get()).thenReturn(sevenZFile);

            try (MockedStatic<SevenZFile> sevenZFileStatic = mockStatic(SevenZFile.class)) {
                sevenZFileStatic.when(SevenZFile::builder).thenReturn(builder);

                Files.createFile(cb7Path);
                Files.setLastModifiedTime(cb7Path, FileTime.fromMillis(System.currentTimeMillis()));

                List<Integer> pages = cbxReaderService.getAvailablePages(1L);
                assertEquals(List.of(1), pages);
            }
        }
    }

    @Test
    void testGetAvailablePages_CBR_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbrPath.toString());

            FileHeader header = mock(FileHeader.class);
            when(header.isDirectory()).thenReturn(false);
            when(header.getFileName()).thenReturn("1.jpg");

            try (MockedConstruction<Archive> ignored = mockConstruction(Archive.class, (mock, context) -> {
                when(mock.getFileHeaders()).thenReturn(List.of(header));
            })) {
                Files.deleteIfExists(cbrPath);
                Files.createFile(cbrPath);
                Files.setLastModifiedTime(cbrPath, FileTime.fromMillis(System.currentTimeMillis()));

                List<Integer> pages = cbxReaderService.getAvailablePages(1L);
                assertEquals(List.of(1), pages);
            }
        }
    }

    @Test
    void testStreamPageImage_CBR_Success() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbrPath.toString());

            FileHeader header = mock(FileHeader.class);
            when(header.isDirectory()).thenReturn(false);
            when(header.getFileName()).thenReturn("1.jpg");

            try (MockedConstruction<Archive> ignored = mockConstruction(Archive.class, (mock, context) -> {
                when(mock.getFileHeaders()).thenReturn(List.of(header));
                doAnswer(invocation -> {
                    OutputStream out = invocation.getArgument(1);
                    out.write(new byte[]{1, 2, 3});
                    return null;
                }).when(mock).extractFile(eq(header), any(OutputStream.class));
            })) {
                Files.deleteIfExists(cbrPath);
                Files.createFile(cbrPath);
                Files.setLastModifiedTime(cbrPath, FileTime.fromMillis(System.currentTimeMillis()));

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                cbxReaderService.streamPageImage(1L, 1, out);
                assertArrayEquals(new byte[]{1, 2, 3}, out.toByteArray());
            }
        }
    }

    @Test
    void testStreamPageImage_PageOutOfRange_Throws() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath.toString());

            ZipArchiveEntry entry1 = new ZipArchiveEntry("1.jpg");
            Enumeration<ZipArchiveEntry> entries = Collections.enumeration(List.of(entry1));
            ZipFile zipFile = mock(ZipFile.class);
            when(zipFile.getEntries()).thenReturn(entries);

            ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(cbzPath)).thenReturn(builder);
            when(builder.setCharset(any(Charset.class))).thenReturn(builder);
            when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
            when(builder.setIgnoreLocalFileHeader(anyBoolean())).thenReturn(builder);
            when(builder.get()).thenReturn(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(cbzPath);
                Files.setLastModifiedTime(cbzPath, FileTime.fromMillis(System.currentTimeMillis()));

                assertThrows(FileNotFoundException.class, () -> cbxReaderService.streamPageImage(1L, 2, new ByteArrayOutputStream()));
            }
        }
    }

    @Test
    void testGetAvailablePages_UnsupportedArchive_Throws() throws Exception {
        Path unknownPath = Path.of("/tmp/test.unknown");
        Files.deleteIfExists(unknownPath);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(unknownPath.toString());

            Files.createFile(unknownPath);
            Files.setLastModifiedTime(unknownPath, FileTime.fromMillis(System.currentTimeMillis()));

            assertThrows(APIException.class, () -> cbxReaderService.getAvailablePages(1L));
        }
    }

    @Test
    void testStreamPageImage_EntryNotFound_Throws() throws Exception {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(bookEntity));
        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.getBookFullPath(bookEntity)).thenReturn(cbzPath.toString());

            ZipArchiveEntry entry1 = new ZipArchiveEntry("1.jpg");
            Enumeration<ZipArchiveEntry> entries = Collections.enumeration(List.of(entry1));
            ZipFile zipFile = mock(ZipFile.class);
            when(zipFile.getEntries()).thenReturn(entries);

            ZipFile.Builder builder = mock(ZipFile.Builder.class, RETURNS_DEEP_STUBS);
            when(builder.setPath(cbzPath)).thenReturn(builder);
            when(builder.setCharset(any(Charset.class))).thenReturn(builder);
            when(builder.setUseUnicodeExtraFields(anyBoolean())).thenReturn(builder);
            when(builder.setIgnoreLocalFileHeader(anyBoolean())).thenReturn(builder);
            when(builder.get()).thenReturn(zipFile);

            try (MockedStatic<ZipFile> zipFileStatic = mockStatic(ZipFile.class)) {
                zipFileStatic.when(ZipFile::builder).thenReturn(builder);

                Files.createFile(cbzPath);
                Files.setLastModifiedTime(cbzPath, FileTime.fromMillis(System.currentTimeMillis()));

                assertThrows(IOException.class, () -> cbxReaderService.streamPageImage(1L, 2, new ByteArrayOutputStream()));
            }
        }
    }
}
