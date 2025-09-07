package com.adityachandel.booklore.service.upload;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.AdditionalFileMapperImpl;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.service.metadata.extractor.EpubMetadataExtractor;
import com.adityachandel.booklore.service.metadata.extractor.PdfMetadataExtractor;
import com.adityachandel.booklore.service.monitoring.MonitoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileUploadServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    LibraryRepository libraryRepository;
    @Mock
    BookRepository bookRepository;
    @Mock
    BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    BookFileProcessorRegistry processorRegistry;
    @Mock
    NotificationService notificationService;
    @Mock
    AppSettingService appSettingService;
    @Mock
    PdfMetadataExtractor pdfMetadataExtractor;
    @Mock
    EpubMetadataExtractor epubMetadataExtractor;
    @Mock
    MonitoringService monitoringService;

    AppProperties appProperties;
    FileUploadService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appProperties = new AppProperties();
        appProperties.setBookdropFolder(tempDir.toString());

        AppSettings settings = new AppSettings();
        settings.setMaxFileUploadSizeInMb(10);
        settings.setUploadPattern("{currentFilename}");
        when(appSettingService.getAppSettings()).thenReturn(settings);

        var additionalFilesMapper = new AdditionalFileMapperImpl();

        service = new FileUploadService(
                libraryRepository, bookRepository, bookAdditionalFileRepository,
                processorRegistry, notificationService,
                appSettingService, appProperties, pdfMetadataExtractor,
                epubMetadataExtractor, additionalFilesMapper, monitoringService
        );

        ReflectionTestUtils.setField(service, "userId", "0");
        ReflectionTestUtils.setField(service, "groupId", "0");
    }

    @Test
    void uploadFileBookDrop_succeeds_and_copiesFile() throws IOException {
        byte[] content = "hello".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);

        assertThat(service.uploadFileBookDrop(file)).isNull();

        Path dropped = tempDir.resolve("test.pdf");
        assertThat(Files.exists(dropped)).isTrue();
        assertThat(Files.readAllBytes(dropped)).isEqualTo(content);
    }

    @Test
    void uploadFileBookDrop_throws_when_duplicate() throws IOException {
        byte[] content = "data".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "dup.epub", "application/epub+zip", content);
        Files.write(tempDir.resolve("dup.epub"), new byte[]{1, 2, 3});

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFileBookDrop(file))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(ApiError.FILE_ALREADY_EXISTS.getStatus());
                    assertThat(ex.getMessage()).isEqualTo(ApiError.FILE_ALREADY_EXISTS.getMessage());
                });
    }

    @Test
    void uploadFileBookDrop_throws_on_invalid_extension() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.txt", "text/plain", "x".getBytes());

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFileBookDrop(file))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(ApiError.INVALID_FILE_FORMAT.getStatus());
                    assertThat(ex.getMessage()).contains("Invalid file format, only pdf and epub are supported");
                });
    }

    @Test
    void uploadFileBookDrop_throws_when_too_large() {
        byte[] content = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", content);
        AppSettings small = new AppSettings();
        small.setMaxFileUploadSizeInMb(1);
        when(appSettingService.getAppSettings()).thenReturn(small);

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFileBookDrop(file))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(ApiError.FILE_TOO_LARGE.getStatus());
                    assertThat(ex.getMessage()).contains("1");
                });
    }

    @Test
    void uploadFile_throws_when_library_not_found() {
        MockMultipartFile file = new MockMultipartFile("file", "book.cbz", "application/octet-stream", new byte[]{1});
        when(libraryRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFile(file, 42L, 1L))
                .satisfies(ex -> assertThat(ex.getStatus()).isEqualTo(ApiError.LIBRARY_NOT_FOUND.getStatus()));
    }

    @Test
    void uploadFile_throws_when_invalid_library_path() {
        MockMultipartFile file = new MockMultipartFile("file", "book.cbz", "application/octet-stream", new byte[]{1});
        LibraryEntity lib = new LibraryEntity();
        lib.setId(42L);
        lib.setLibraryPaths(List.of());
        when(libraryRepository.findById(42L)).thenReturn(Optional.of(lib));

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFile(file, 42L, 99L))
                .satisfies(ex -> assertThat(ex.getStatus()).isEqualTo(ApiError.INVALID_LIBRARY_PATH.getStatus()));
    }

    @Test
    void uploadFile_throws_on_transfer_io_exception() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("book.cbz");
        when(file.getSize()).thenReturn(100L);
        when(file.getName()).thenReturn("file");
        doThrow(new IOException("disk error")).when(file).transferTo(any(Path.class));

        LibraryEntity lib = new LibraryEntity();
        lib.setId(1L);
        LibraryPathEntity path = new LibraryPathEntity();
        path.setId(1L);
        path.setPath(tempDir.toString());
        lib.setLibraryPaths(List.of(path));
        when(libraryRepository.findById(1L)).thenReturn(Optional.of(lib));

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFile(file, 1L, 1L))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(ApiError.FILE_READ_ERROR.getStatus());
                    assertThat(ex.getMessage()).contains("Error reading files from path");
                });
    }

    @Test
    void uploadFile_succeeds_and_processes() throws IOException {
        byte[] data = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "book.cbz", "application/octet-stream", data);

        LibraryEntity lib = new LibraryEntity();
        lib.setId(7L);
        LibraryPathEntity path = new LibraryPathEntity();
        path.setId(2L);
        path.setPath(tempDir.toString());
        lib.setLibraryPaths(List.of(path));
        when(libraryRepository.findById(7L)).thenReturn(Optional.of(lib));

        BookFileProcessor proc = mock(BookFileProcessor.class);
        FileProcessResult fileProcessResult = FileProcessResult.builder()
                .book(Book.builder().build())
                .status(FileProcessStatus.NEW)
                .build();

        when(processorRegistry.getProcessorOrThrow(BookFileType.CBX)).thenReturn(proc);
        when(proc.processFile(any())).thenReturn(fileProcessResult);

        Book result = service.uploadFile(file, 7L, 2L);

        assertThat(result).isSameAs(fileProcessResult.getBook());
        Path moved = tempDir.resolve("book.cbz");
        assertThat(Files.exists(moved)).isTrue();
        verify(notificationService).sendMessage(eq(Topic.BOOK_ADD), same(fileProcessResult.getBook()));
        verifyNoInteractions(pdfMetadataExtractor, epubMetadataExtractor);
    }
}
