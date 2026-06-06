package org.booklore;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMapper;
import org.booklore.repository.*;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.book.BookService;
import org.booklore.service.book.BookUpdateService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.FileStreamingService;
import org.booklore.service.audit.AuditService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class BookServiceDeleteTests {

    private BookService bookService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        BookRepository bookRepository = Mockito.mock(BookRepository.class);
        BookFileRepository bookFileRepository = Mockito.mock(BookFileRepository.class);
        PdfViewerPreferencesRepository pdfViewerPreferencesRepository = Mockito.mock(PdfViewerPreferencesRepository.class);
        EbookViewerPreferenceRepository ebookViewerPreferenceRepository = Mockito.mock(EbookViewerPreferenceRepository.class);
        CbxViewerPreferencesRepository cbxViewerPreferencesRepository = Mockito.mock(CbxViewerPreferencesRepository.class);
        NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository = Mockito.mock(NewPdfViewerPreferencesRepository.class);
        FileService fileService = Mockito.mock(FileService.class);
        BookMapper bookMapper = Mockito.mock(BookMapper.class);
        UserBookProgressRepository userBookProgressRepository = Mockito.mock(UserBookProgressRepository.class);
        AuthenticationService authenticationService = Mockito.mock(AuthenticationService.class);
        BookQueryService bookQueryService = Mockito.mock(BookQueryService.class);
        ReadingProgressService readingProgressService = Mockito.mock(ReadingProgressService.class);
        BookDownloadService bookDownloadService = Mockito.mock(BookDownloadService.class);
        MonitoringRegistrationService monitoringRegistrationService = Mockito.mock(MonitoringRegistrationService.class);
        BookUpdateService bookUpdateService = Mockito.mock(BookUpdateService.class);
        SidecarMetadataWriter sidecarMetadataWriter = Mockito.mock(SidecarMetadataWriter.class);
        FileStreamingService fileStreamingService = Mockito.mock(FileStreamingService.class);
        AuditService auditService = Mockito.mock(AuditService.class);

        bookService = new BookService(
                bookRepository,
                bookFileRepository,
                pdfViewerPreferencesRepository,
                cbxViewerPreferencesRepository,
                newPdfViewerPreferencesRepository,
                fileService,
                bookMapper,
                userBookProgressRepository,
                authenticationService,
                bookQueryService,
                readingProgressService,
                bookDownloadService,
                monitoringRegistrationService,
                bookUpdateService,
                ebookViewerPreferenceRepository,
                sidecarMetadataWriter,
                fileStreamingService,
                auditService
        );
    }

    @Test
    void deletesEmptyDirectoriesUpToLibraryRoot() throws IOException {
        Path libraryRoot = tempDir.resolve("libraryRoot");
        Files.createDirectories(libraryRoot);

        Path nestedDir1 = libraryRoot.resolve("1");
        Path nestedDir2 = nestedDir1.resolve("2");
        Path nestedDir3 = nestedDir2.resolve("3");
        Files.createDirectories(nestedDir3);

        bookService.deleteEmptyParentDirsUpToLibraryFolders(nestedDir3, Set.of(libraryRoot));

        assertThat(Files.exists(nestedDir3)).isFalse();
        assertThat(Files.exists(nestedDir2)).isFalse();
        assertThat(Files.exists(nestedDir1)).isFalse();
        assertThat(Files.exists(libraryRoot)).isTrue();
    }

    @Test
    void doesNotDeleteDirectoryWithImportantFile() throws IOException {
        Path libraryRoot = tempDir.resolve("libraryRoot");
        Files.createDirectories(libraryRoot);

        Path nestedDir = libraryRoot.resolve("nested");
        Files.createDirectories(nestedDir);

        Path importantFile = nestedDir.resolve("important.txt");
        Files.createFile(importantFile);

        bookService.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot));

        assertThat(Files.exists(nestedDir)).isTrue();
        assertThat(Files.exists(importantFile)).isTrue();
        assertThat(Files.exists(libraryRoot)).isTrue();
    }

    @Test
    void deletesIgnoredFilesBeforeDeletingDirectory() throws IOException {
        Path libraryRoot = tempDir.resolve("libraryRoot");
        Files.createDirectories(libraryRoot);

        Path nestedDir = libraryRoot.resolve("nested");
        Files.createDirectories(nestedDir);

        Path ignoredFile = nestedDir.resolve(".DS_Store");
        Files.createFile(ignoredFile);

        bookService.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot));

        assertThat(Files.exists(ignoredFile)).isFalse();
        assertThat(Files.exists(nestedDir)).isFalse();
        assertThat(Files.exists(libraryRoot)).isTrue();
    }

    @Test
    void stopsAtLibraryRoot() throws IOException {
        Path libraryRoot = tempDir.resolve("libraryRoot");
        Files.createDirectories(libraryRoot);

        bookService.deleteEmptyParentDirsUpToLibraryFolders(libraryRoot, Set.of(libraryRoot));

        assertThat(Files.exists(libraryRoot)).isTrue();
    }

    @Test
    void handlesUnreadableDirectoryGracefully() throws IOException {
        Path libraryRoot = tempDir.resolve("libraryRoot");
        Files.createDirectories(libraryRoot);

        Path nestedDir = libraryRoot.resolve("nested");
        Files.createDirectories(nestedDir);

        File nestedDirFile = nestedDir.toFile();

        boolean readableBefore = nestedDirFile.canRead();

        try {
            if (nestedDirFile.setReadable(false)) {
                assertThatCode(() -> bookService.deleteEmptyParentDirsUpToLibraryFolders(nestedDir, Set.of(libraryRoot)))
                        .doesNotThrowAnyException();
            } else {
                System.out.println("Could not change read permission; skipping unreadable directory test.");
            }
        } finally {
            nestedDirFile.setReadable(readableBefore);
        }
    }
}