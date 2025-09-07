package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.response.BookDeletionResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.monitoring.MonitoringProtectionService;
import com.adityachandel.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceDeleteBooksTest {

    @Mock private BookRepository bookRepository;
    @Mock private PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    @Mock private EpubViewerPreferencesRepository epubViewerPreferencesRepository;
    @Mock private CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    @Mock private NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private FileService fileService;
    @Mock private BookMapper bookMapper;
    @Mock private UserRepository userRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private BookQueryService bookQueryService;
    @Mock private UserProgressService userProgressService;
    @Mock private BookDownloadService bookDownloadService;
    @Mock private MonitoringProtectionService monitoringProtectionService;

    private BookService bookService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        bookService = new BookService(
                bookRepository,
                pdfViewerPreferencesRepository,
                epubViewerPreferencesRepository,
                cbxViewerPreferencesRepository,
                newPdfViewerPreferencesRepository,
                shelfRepository,
                fileService,
                bookMapper,
                userRepository,
                userBookProgressRepository,
                authenticationService,
                bookQueryService,
                userProgressService,
                bookDownloadService,
                monitoringProtectionService
        );

        // Configure mock to execute the Supplier for file operations
        when(monitoringProtectionService.executeWithProtection(any(Supplier.class), eq("book deletion")))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
    }

    private LibraryEntity createTestLibrary() {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());

        LibraryEntity library = new LibraryEntity();
        library.setId(1L);
        library.setName("Test Library");
        library.setLibraryPaths(List.of(libraryPath));
        
        return library;
    }

    private LibraryPathEntity createTestLibraryPath() {
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(tempDir.toString());
        return libraryPath;
    }

    @Test
    void deleteBooks_successfulDeletion() throws IOException {
        // Arrange
        Path testFile1 = tempDir.resolve("book1.epub");
        Path testFile2 = tempDir.resolve("book2.pdf");
        Files.createFile(testFile1);
        Files.createFile(testFile2);

        LibraryEntity library = createTestLibrary();
        LibraryPathEntity libraryPath = createTestLibraryPath();

        BookEntity book1 = BookEntity.builder()
                .id(1L)
                .fileName("book1.epub")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        BookEntity book2 = BookEntity.builder()
                .id(2L)
                .fileName("book2.pdf")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        List<BookEntity> books = List.of(book1, book2);
        when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L))).thenReturn(books);

        // Act
        ResponseEntity<BookDeletionResponse> response = bookService.deleteBooks(Set.of(1L, 2L));

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        BookDeletionResponse deletionResponse = response.getBody();
        assertThat(deletionResponse).isNotNull();
        assertThat(deletionResponse.getDeleted()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(deletionResponse.getFailedFileDeletions()).isEmpty();

        // Verify monitoring protection was used
        verify(monitoringProtectionService).executeWithProtection(any(Supplier.class), eq("book deletion"));
        
        // Verify books were deleted from database
        verify(bookRepository).deleteAll(books);
        
        // Verify files were deleted
        assertThat(Files.exists(testFile1)).isFalse();
        assertThat(Files.exists(testFile2)).isFalse();
    }

    @Test
    void deleteBooks_fileNotFound() {
        // Arrange
        Path nonExistentFile = tempDir.resolve("non-existent.epub");

        LibraryEntity library = createTestLibrary();
        LibraryPathEntity libraryPath = createTestLibraryPath();

        BookEntity book = BookEntity.builder()
                .id(1L)
                .fileName("non-existent.epub")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));

        // Act
        ResponseEntity<BookDeletionResponse> response = bookService.deleteBooks(Set.of(1L));

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        BookDeletionResponse deletionResponse = response.getBody();
        assertThat(deletionResponse).isNotNull();
        assertThat(deletionResponse.getDeleted()).containsExactly(1L);
        
        // File doesn't exist, so no deletion failure is recorded
        assertThat(deletionResponse.getFailedFileDeletions()).isEmpty();

        verify(monitoringProtectionService).executeWithProtection(any(Supplier.class), eq("book deletion"));
        verify(bookRepository).deleteAll(List.of(book));
    }

    @Test
    void deleteBooks_partialFailure() throws IOException {
        // Arrange
        Path existingFile = tempDir.resolve("existing.epub");
        Files.createFile(existingFile);

        LibraryEntity library = createTestLibrary();
        LibraryPathEntity libraryPath = createTestLibraryPath();

        BookEntity existingBook = BookEntity.builder()
                .id(1L)
                .fileName("existing.epub")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        BookEntity missingBook = BookEntity.builder()
                .id(2L)
                .fileName("missing.epub")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        List<BookEntity> books = List.of(existingBook, missingBook);
        when(bookQueryService.findAllWithMetadataByIds(Set.of(1L, 2L))).thenReturn(books);

        // Act
        ResponseEntity<BookDeletionResponse> response = bookService.deleteBooks(Set.of(1L, 2L));

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        BookDeletionResponse deletionResponse = response.getBody();
        assertThat(deletionResponse).isNotNull();
        assertThat(deletionResponse.getDeleted()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(deletionResponse.getFailedFileDeletions()).isEmpty(); // Missing file is not considered a failure

        verify(monitoringProtectionService).executeWithProtection(any(Supplier.class), eq("book deletion"));
        verify(bookRepository).deleteAll(books);
        
        // Existing file should be deleted
        assertThat(Files.exists(existingFile)).isFalse();
    }

    @Test
    void deleteBooks_emptySet() {
        // Act
        ResponseEntity<BookDeletionResponse> response = bookService.deleteBooks(Set.of());

        // Assert
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        BookDeletionResponse deletionResponse = response.getBody();
        assertThat(deletionResponse).isNotNull();
        assertThat(deletionResponse.getDeleted()).isEmpty();
        assertThat(deletionResponse.getFailedFileDeletions()).isEmpty();

        // Should still use monitoring protection even for empty operations
        verify(monitoringProtectionService).executeWithProtection(any(Supplier.class), eq("book deletion"));
        verify(bookRepository).deleteAll(List.of());
    }

    @Test
    void deleteBooks_verifyMonitoringProtectionUsage() {
        // Arrange
        LibraryEntity library = createTestLibrary();
        LibraryPathEntity libraryPath = createTestLibraryPath();
        
        BookEntity book = BookEntity.builder()
                .id(1L)
                .fileName("test.epub")
                .fileSubPath("")
                .libraryPath(libraryPath)
                .library(library)
                .build();

        when(bookQueryService.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));

        // Act
        bookService.deleteBooks(Set.of(1L));

        // Assert - Verify monitoring protection is called with correct parameters
        verify(monitoringProtectionService).executeWithProtection(
            any(Supplier.class), 
            eq("book deletion")
        );
    }
}