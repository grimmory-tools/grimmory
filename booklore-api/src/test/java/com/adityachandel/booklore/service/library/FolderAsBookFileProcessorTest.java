package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.event.AdminEventBroadcaster;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.FileFingerprint;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import com.adityachandel.booklore.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderAsBookFileProcessorTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookAdditionalFileRepository bookAdditionalFileRepository;

    @Mock
    private BookEventBroadcaster bookEventBroadcaster;

    @Mock
    private AdminEventBroadcaster adminEventBroadcaster;

    @Mock
    private BookFileProcessorRegistry bookFileProcessorRegistry;

    @Mock
    private BookFileProcessor mockBookFileProcessor;

    @InjectMocks
    private FolderAsBookFileProcessor processor;

    @Captor
    private ArgumentCaptor<BookAdditionalFileEntity> additionalFileCaptor;

    private MockedStatic<FileUtils> fileUtilsMock;
    private MockedStatic<FileFingerprint> fileFingerprintMock;

    @BeforeEach
    void setUp() {
        fileUtilsMock = mockStatic(FileUtils.class);
        fileFingerprintMock = mockStatic(FileFingerprint.class);
        // Setup common FileUtils mocks for all tests
        fileUtilsMock.when(() -> FileUtils.getFileSizeInKb(any(Path.class))).thenReturn(100L);
        fileFingerprintMock.when(() -> FileFingerprint.generateHash(any(Path.class)))
                .then(invocation -> {
                    MessageDigest digest = MessageDigest.getInstance("MD5");
                    Path path = invocation.getArgument(0);
                    byte[] hash = digest.digest(path.toString().getBytes());
                    StringBuilder hexString = new StringBuilder();
                    for (byte b : hash) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) hexString.append('0');
                        hexString.append(hex);
                    }
                    return hexString.toString();
                });
    }

    @AfterEach
    void tearDown() {
        fileUtilsMock.close();
        fileFingerprintMock.close();
    }

    @Test
    void getScanMode_shouldReturnFolderAsBook() {
        assertThat(processor.getScanMode()).isEqualTo(LibraryScanMode.FOLDER_AS_BOOK);
    }

    @Test
    void supportsSupplementaryFiles_shouldReturnTrue() {
        assertThat(processor.supportsSupplementaryFiles()).isTrue();
    }

    @Test
    void processLibraryFiles_shouldCreateNewBookFromDirectory() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = createLibraryFilesInSameDirectory();

        Book createdBook = Book.builder()
                .id(1L)
                .fileName("book.pdf")
                .bookType(BookFileType.PDF)
                .build();

        BookEntity bookEntity = createBookEntity(1L, "book.pdf", "books");

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), anyString()))
                .thenReturn(new ArrayList<>());
        when(bookFileProcessorRegistry.getProcessorOrThrow(BookFileType.PDF))
                .thenReturn(mockBookFileProcessor);
        when(mockBookFileProcessor.processFile(any(LibraryFile.class)))
                .thenReturn(new FileProcessResult(createdBook, FileProcessStatus.NEW, null));
        when(bookRepository.getReferenceById(createdBook.getId()))
                .thenReturn(bookEntity);
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor).processFile(any(LibraryFile.class));
        verify(bookEventBroadcaster).broadcastBookAddEvent(createdBook);
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, times(2)).save(additionalFileCaptor.capture());

        List<BookAdditionalFileEntity> capturedFiles = additionalFileCaptor.getAllValues();
        assertThat(capturedFiles).hasSize(2);
        assertThat(capturedFiles).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("book.epub", "cover.jpg");
        assertThat(capturedFiles).extracting(BookAdditionalFileEntity::getAdditionalFileType)
                .containsExactly(AdditionalFileType.ALTERNATIVE_FORMAT, AdditionalFileType.SUPPLEMENTARY);
    }

    @Test
    void processLibraryFiles_shouldProcessExistingBook() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = createLibraryFilesInSameDirectory()
                .stream()
                .filter(f -> !f.getFileName().equals("book.pdf"))
                .toList();

        BookEntity existingBook = createBookEntity(1L, "book.pdf", "books");

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), eq("books")))
                .thenReturn(List.of(existingBook));
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor, never()).processFile(any());
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, times(2)).save(additionalFileCaptor.capture());

        List<BookAdditionalFileEntity> capturedFiles = additionalFileCaptor.getAllValues();
        assertThat(capturedFiles).hasSize(2);
        assertThat(capturedFiles).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("book.epub", "cover.jpg");
    }

    @Test
    void processLibraryFiles_shouldProcessFilesWithParentBook() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = List.of(
                createLibraryFile("file1.txt", "books/chapter1"),
                createLibraryFile("file2.txt", "books/chapter1")
        );

        BookEntity parentBook = createBookEntity(1L, "book.pdf", "books");

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), eq("books/chapter1")))
                .thenReturn(new ArrayList<>());
        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), eq("books")))
                .thenReturn(List.of(parentBook));
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor, never()).processFile(any());
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, times(2)).save(additionalFileCaptor.capture());

        List<BookAdditionalFileEntity> capturedFiles = additionalFileCaptor.getAllValues();
        assertThat(capturedFiles).hasSize(2);
        assertThat(capturedFiles).extracting(BookAdditionalFileEntity::getAdditionalFileType)
                .containsOnly(AdditionalFileType.SUPPLEMENTARY);
    }

    @Test
    void processLibraryFiles_shouldRespectDefaultBookFormat() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        libraryEntity.setDefaultBookFormat(BookFileType.EPUB);

        List<LibraryFile> libraryFiles = List.of(
                createLibraryFile("book.pdf", "books", BookFileType.PDF),
                createLibraryFile("book.epub", "books", BookFileType.EPUB),
                createLibraryFile("book.cbz", "books", BookFileType.CBX)
        );

        Book createdBook = Book.builder()
                .id(1L)
                .fileName("book.epub")
                .bookType(BookFileType.EPUB)
                .build();

        BookEntity bookEntity = createBookEntity(1L, "book.epub", "books");

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), anyString()))
                .thenReturn(new ArrayList<>());
        when(bookFileProcessorRegistry.getProcessorOrThrow(BookFileType.EPUB))
                .thenReturn(mockBookFileProcessor);
        when(mockBookFileProcessor.processFile(argThat(file -> file.getFileName().equals("book.epub"))))
                .thenReturn(new FileProcessResult(createdBook, FileProcessStatus.NEW, null));
        when(bookRepository.getReferenceById(createdBook.getId()))
                .thenReturn(bookEntity);
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor).processFile(argThat(file -> file.getFileName().equals("book.epub")));
        verify(bookEventBroadcaster).broadcastBookAddEvent(createdBook);
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, times(2)).save(additionalFileCaptor.capture());

        List<BookAdditionalFileEntity> capturedFiles = additionalFileCaptor.getAllValues();
        assertThat(capturedFiles).extracting(BookAdditionalFileEntity::getFileName)
                .containsExactlyInAnyOrder("book.pdf", "book.cbz");
    }

    @Test
    void processLibraryFiles_shouldUseDefaultPriorityWhenNoDefaultFormat() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        libraryEntity.setDefaultBookFormat(null);

        List<LibraryFile> libraryFiles = List.of(
                createLibraryFile("book.epub", "books", BookFileType.EPUB),
                createLibraryFile("book.cbz", "books", BookFileType.CBX),
                createLibraryFile("book.pdf", "books", BookFileType.PDF)
        );

        Book createdBook = Book.builder()
                .id(1L)
                .fileName("book.pdf")
                .bookType(BookFileType.PDF)
                .build();

        BookEntity bookEntity = createBookEntity(1L, "book.pdf", "books");

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), anyString()))
                .thenReturn(new ArrayList<>());
        when(bookFileProcessorRegistry.getProcessorOrThrow(BookFileType.PDF))
                .thenReturn(mockBookFileProcessor);
        when(mockBookFileProcessor.processFile(argThat(file -> file.getFileName().equals("book.pdf"))))
                .thenReturn(new FileProcessResult(createdBook, FileProcessStatus.NEW, null));
        when(bookRepository.getReferenceById(createdBook.getId()))
                .thenReturn(bookEntity);
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor).processFile(argThat(file -> file.getFileName().equals("book.pdf")));
        verify(bookEventBroadcaster).broadcastBookAddEvent(createdBook);
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
    }

    @Test
    void processLibraryFiles_shouldSkipExistingAdditionalFiles() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = createLibraryFilesInSameDirectory()
                .stream()
                .filter(f -> !f.getFileName().equals("book.pdf"))
                .toList();

        BookEntity existingBook = createBookEntity(1L, "book.pdf", "books");
        BookAdditionalFileEntity existingAdditionalFile = BookAdditionalFileEntity.builder()
                .id(1L)
                .book(existingBook)
                .fileName("book.epub")
                .fileSubPath("books")
                .additionalFileType(AdditionalFileType.ALTERNATIVE_FORMAT)
                .build();

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), eq("books")))
                .thenReturn(List.of(existingBook));
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), eq("books"), eq("book.epub")))
                .thenReturn(Optional.of(existingAdditionalFile));
        when(bookAdditionalFileRepository.findByLibraryPath_IdAndFileSubPathAndFileName(anyLong(), eq("books"), eq("cover.jpg")))
                .thenReturn(Optional.empty());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, times(1)).save(additionalFileCaptor.capture());

        BookAdditionalFileEntity capturedFile = additionalFileCaptor.getValue();
        assertThat(capturedFile.getFileName()).isEqualTo("cover.jpg");
    }

    @Test
    void processLibraryFiles_shouldHandleEmptyDirectory() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = List.of(
                createLibraryFile("readme.txt", "docs"),
                createLibraryFile("notes.txt", "docs")
        );

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), anyString()))
                .thenReturn(new ArrayList<>());

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(mockBookFileProcessor, never()).processFile(any());
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(adminEventBroadcaster, never()).broadcastAdminEvent(anyString());
        verify(bookAdditionalFileRepository, never()).save(any());
    }

    @Test
    void processLibraryFiles_shouldHandleProcessorError() {
        // Given
        LibraryEntity libraryEntity = createLibraryEntity();
        List<LibraryFile> libraryFiles = List.of(
                createLibraryFile("book.pdf", "books", BookFileType.PDF)
        );

        when(bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(anyLong(), anyString()))
                .thenReturn(new ArrayList<>());
        when(bookFileProcessorRegistry.getProcessorOrThrow(BookFileType.PDF))
                .thenReturn(mockBookFileProcessor);
        when(mockBookFileProcessor.processFile(any(LibraryFile.class)))
                .thenThrow(new RuntimeException("Processing error"));

        // When
        processor.processLibraryFiles(libraryFiles, libraryEntity);

        // Then
        verify(bookEventBroadcaster, never()).broadcastBookAddEvent(any());
        verify(bookAdditionalFileRepository, never()).save(any());
        verify(adminEventBroadcaster).broadcastAdminEvent(anyString());
    }

    // Helper methods
    private LibraryEntity createLibraryEntity() {
        LibraryEntity library = new LibraryEntity();
        library.setId(1L);
        library.setName("Test Library");
        library.setScanMode(LibraryScanMode.FOLDER_AS_BOOK);

        LibraryPathEntity path = new LibraryPathEntity();
        path.setId(1L);
        path.setPath("/test/library");
        library.setLibraryPaths(List.of(path));

        return library;
    }

    private List<LibraryFile> createLibraryFilesInSameDirectory() {
        return List.of(
                createLibraryFile("book.pdf", "books", BookFileType.PDF),
                createLibraryFile("book.epub", "books", BookFileType.EPUB),
                createLibraryFile("cover.jpg", "books")
        );
    }

    private LibraryFile createLibraryFile(String fileName, String subPath) {
        return createLibraryFile(fileName, subPath, null);
    }

    private LibraryFile createLibraryFile(String fileName, String subPath, BookFileType bookFileType) {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/test/library");

        return LibraryFile.builder()
                .libraryEntity(new LibraryEntity())
                .libraryPathEntity(libraryPath)
                .fileName(fileName)
                .fileSubPath(subPath)
                .bookFileType(bookFileType)
                .build();
    }

    private BookEntity createBookEntity(Long id, String fileName, String subPath) {
        BookEntity book = new BookEntity();
        book.setId(id);
        book.setFileName(fileName);
        book.setFileSubPath(subPath);
        book.setBookType(BookFileType.PDF);
        book.setAddedOn(Instant.now());

        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/test/library");
        book.setLibraryPath(libraryPath);

        return book;
    }
}
