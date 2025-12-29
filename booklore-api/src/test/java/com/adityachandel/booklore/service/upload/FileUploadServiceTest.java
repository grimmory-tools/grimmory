package com.adityachandel.booklore.service.upload;

import com.adityachandel.booklore.config.AppProperties;
import com.adityachandel.booklore.exception.APIException;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.AdditionalFileMapper;
import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.FileMovingHelper;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

class FileUploadServiceTest {

    public static final Set<String> LONG_AUTHOR_LIST = new LinkedHashSet<>(List.of(
        "梁思成", "叶嘉莹", "厉以宁", "萧乾", "冯友兰", "费孝通", "李济", "侯仁之", "汤一介", "温源宁",
        "胡适", "吴青", "李照国", "蒋梦麟", "汪荣祖", "邢玉瑞", "《中华思想文化术语》编委会",
        "北京大学政策法规研究室", "（美）艾恺（Guy S. Alitto）", "顾毓琇", "陈从周",
        "（加拿大）伊莎白（Isabel Crook）（美）柯临清（Christina Gilmartin）", "傅莹"
    ));

    @TempDir
    Path tempDir;

    @Mock
    LibraryRepository libraryRepository;
    @Mock
    BookRepository bookRepository;
    @Mock
    BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock
    AppSettingService appSettingService;
    @Mock
    MetadataExtractorFactory metadataExtractorFactory;
    @Mock
    FileMovingHelper fileMovingHelper;
    @Mock
    AdditionalFileMapper additionalFileMapper;

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

        service = new FileUploadService(
                libraryRepository, bookRepository, bookAdditionalFileRepository,
                appSettingService, appProperties, metadataExtractorFactory, additionalFileMapper, fileMovingHelper
        );
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
        when(fileMovingHelper.getFileNamingPattern(lib)).thenReturn("{currentFilename}");

        assertThatExceptionOfType(APIException.class)
                .isThrownBy(() -> service.uploadFile(file, 1L, 1L))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(ApiError.FILE_READ_ERROR.getStatus());
                    assertThat(ex.getMessage()).contains("Error reading files from path");
                });
    }

    @Test
    void uploadFile_succeeds_and_processes() {
        byte[] data = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "book.cbz", "application/octet-stream", data);

        LibraryEntity lib = new LibraryEntity();
        lib.setId(7L);
        LibraryPathEntity path = new LibraryPathEntity();
        path.setId(2L);
        path.setPath(tempDir.toString());
        lib.setLibraryPaths(List.of(path));
        when(libraryRepository.findById(7L)).thenReturn(Optional.of(lib));
        when(fileMovingHelper.getFileNamingPattern(lib)).thenReturn("{currentFilename}");

        service.uploadFile(file, 7L, 2L);

        Path moved = tempDir.resolve("book.cbz");
        assertThat(Files.exists(moved)).isTrue();
    }

    @Test
    void uploadAdditionalFile_successful_and_saves_entity() {
        long bookId = 5L;
        MockMultipartFile file = new MockMultipartFile("file", "add.pdf", "application/pdf", "payload".getBytes());

        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setId(1L);
        libPath.setPath(tempDir.toString());
        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setLibraryPath(libPath);
        book.setFileSubPath(".");

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        try (MockedStatic<FileFingerprint> fp = mockStatic(FileFingerprint.class)) {
            fp.when(() -> FileFingerprint.generateHash(any())).thenReturn("hash-123");

            when(bookAdditionalFileRepository.findByAltFormatCurrentHash("hash-123")).thenReturn(Optional.empty());

            when(bookAdditionalFileRepository.save(any(BookAdditionalFileEntity.class))).thenAnswer(inv -> {
                BookAdditionalFileEntity e = inv.getArgument(0);
                e.setId(99L);
                return e;
            });

            AdditionalFile dto = mock(AdditionalFile.class);
            when(additionalFileMapper.toAdditionalFile(any(BookAdditionalFileEntity.class))).thenReturn(dto);

            AdditionalFile result = service.uploadAdditionalFile(bookId, file, AdditionalFileType.ALTERNATIVE_FORMAT, "desc");

            assertThat(result).isEqualTo(dto);
            verify(bookAdditionalFileRepository).save(any(BookAdditionalFileEntity.class));
            verify(additionalFileMapper).toAdditionalFile(any(BookAdditionalFileEntity.class));
        }
    }

    @Test
    void uploadAdditionalFile_duplicate_alternative_format_throws() {
        long bookId = 6L;
        MockMultipartFile file = new MockMultipartFile("file", "alt.pdf", "application/pdf", "payload".getBytes());

        LibraryPathEntity libPath = new LibraryPathEntity();
        libPath.setId(2L);
        libPath.setPath(tempDir.toString());
        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setLibraryPath(libPath);
        book.setFileSubPath(".");

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        try (MockedStatic<FileFingerprint> fp = mockStatic(FileFingerprint.class)) {
            fp.when(() -> FileFingerprint.generateHash(any())).thenReturn("dup-hash");

            BookAdditionalFileEntity existing = new BookAdditionalFileEntity();
            existing.setId(1L);
            when(bookAdditionalFileRepository.findByAltFormatCurrentHash("dup-hash")).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> service.uploadAdditionalFile(bookId, file, AdditionalFileType.ALTERNATIVE_FORMAT, null));
        }
    }

    @Test
    @DisplayName("Should upload files with long authors without filesystem errors")
    void uploadFile_withLongAuthors_doesNotThrowFilesystemError() {
        byte[] data = "content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "long-authors.epub", "application/epub+zip", data);

        LibraryEntity lib = new LibraryEntity();
        lib.setId(10L);
        String defaultPattern = "{authors}/<{series}/><{seriesIndex}. >{title}< - {authors}>< ({year})>";
        lib.setFileNamingPattern(defaultPattern);
        LibraryPathEntity path = new LibraryPathEntity();
        path.setId(3L);
        path.setPath(tempDir.toString());
        lib.setLibraryPaths(List.of(path));
        when(libraryRepository.findById(10L)).thenReturn(Optional.of(lib));

        BookMetadata metadata = BookMetadata.builder()
                .title("中国文化合集")
                .authors(LONG_AUTHOR_LIST)
                .build();

        when(metadataExtractorFactory.extractMetadata(any(BookFileExtension.class), any(File.class))).thenReturn(metadata);

        assertDoesNotThrow(() -> service.uploadFile(file, 10L, 3L));
    }
}
