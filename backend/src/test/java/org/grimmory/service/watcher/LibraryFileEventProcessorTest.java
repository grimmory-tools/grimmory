package org.grimmory.service.watcher;

import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.entity.LibraryPathEntity;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.model.enums.LibraryOrganizationMode;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.LibraryRepository;
import org.grimmory.service.library.LibraryProcessingService;
import org.grimmory.service.library.LibraryScanListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LibraryFileEventProcessorTest {

    @Mock private LibraryRepository libraryRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookFileTransactionalHandler bookFileTransactionalHandler;
    @Mock private BookFilePersistenceService bookFilePersistenceService;
    @Mock private LibraryProcessingService libraryProcessingService;
    @Mock private LibraryScanListener libraryScanListener;
    @Mock private PendingDeletionPool pendingDeletionPool;

    private LibraryFileEventProcessor processor;

    private AutoCloseable mocks;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        processor = new LibraryFileEventProcessor(
                libraryRepository, bookRepository, bookFileTransactionalHandler,
                bookFilePersistenceService, libraryProcessingService, libraryScanListener, pendingDeletionPool);

        libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath(tempDir.toString());

        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .libraryPaths(List.of(libraryPath))
                .organizationMode(LibraryOrganizationMode.AUTO_DETECT)
                .build();

        when(libraryRepository.findByIdWithPaths(1L)).thenReturn(Optional.of(library));
        when(bookFilePersistenceService.findMatchingLibraryPath(eq(library), any(Path.class)))
                .thenReturn(tempDir.toString());
        when(bookFilePersistenceService.getLibraryPathEntityForFile(eq(library), eq(tempDir.toString())))
                .thenReturn(libraryPath);

        // Start the event processing thread (normally done by SmartLifecycle)
        processor.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        processor.stop();
        mocks.close();
    }

    @Nested
    class HasPendingEventsForPaths {

        @Test
        void returnsFalseWhenNothingPending() {
            assertThat(processor.hasPendingEventsForPaths(Set.of(tempDir))).isFalse();
        }

        @Test
        void delegatesToPendingDeletionPool() {
            when(pendingDeletionPool.hasPendingForPaths(any())).thenReturn(true);

            assertThat(processor.hasPendingEventsForPaths(Set.of(tempDir))).isTrue();
        }
    }

    @Nested
    class ProcessEventDebouncing {

        @Test
        void createCancelsExistingDelete() throws Exception {
            Path file = tempDir.resolve("test.epub");
            Files.writeString(file, "content");

            // Schedule a DELETE
            processor.processEvent(StandardWatchEventKinds.ENTRY_DELETE, 1L, file, false);

            // Immediately follow with CREATE (simulates quick rename)
            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, file, false);

            // The DELETE should have been cancelled. Wait a bit for debounce to settle.
            Thread.sleep(1000);

            // The DELETE event should not have been processed
            verify(bookFilePersistenceService, never()).findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString());
        }

        @Test
        void directoryCreateScheduledWithDelay() throws IOException {
            Path folder = tempDir.resolve("newFolder");
            Files.createDirectory(folder);
            Files.writeString(folder.resolve("test.epub"), "content");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            // Should not process immediately due to folder debounce
            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }

        @Test
        void fileInsidePendingFolderResetsFolderTimer() throws IOException {
            Path folder = tempDir.resolve("audioFolder");
            Files.createDirectory(folder);

            // Trigger folder create first
            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            // Then file inside it
            Path file = folder.resolve("track01.m4b");
            Files.writeString(file, "audio content");
            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, file, false);

            // The folder debounce should have been reset, not processed yet
            verify(bookFileTransactionalHandler, never()).handleNewFolderAudiobook(anyLong(), any());
        }

        @Test
        void nonBookFileEventsIgnored() throws Exception {
            Path textFile = tempDir.resolve("readme.txt");
            Files.writeString(textFile, "hello");

            // Queue a modify event for a non-book file
            processor.processEvent(StandardWatchEventKinds.ENTRY_MODIFY, 1L, textFile, false);

            // Wait for processing
            Thread.sleep(200);

            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }
    }

    @Nested
    class FolderCreateHandling {

        @Test
        void bookPerFileMode_processesFilesIndividually() throws Exception {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FILE);

            Path folder = tempDir.resolve("books");
            Files.createDirectory(folder);
            Files.writeString(folder.resolve("book1.epub"), "content1");
            Files.writeString(folder.resolve("book2.pdf"), "content2");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            // Folder debounce is 5s, then queue processing
            Thread.sleep(8000);

            verify(bookFileTransactionalHandler, times(2)).handleNewBookFile(eq(1L), any());
        }

        @Test
        void bookPerFileMode_ignoresFolderWithIgnoreFile() throws Exception {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FILE);

            Path folder = tempDir.resolve("ignored");
            Files.createDirectory(folder);
            Files.writeString(folder.resolve(".ignore"), "");
            Files.writeString(folder.resolve("book.epub"), "content");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            Thread.sleep(8000);

            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }

        @Test
        void bookPerFileMode_skipsFilesUnderIgnoredSubdirectory() throws Exception {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FILE);

            Path folder = tempDir.resolve("parent");
            Files.createDirectory(folder);
            Files.writeString(folder.resolve("good.epub"), "content");

            Path ignoredSub = folder.resolve("skipped");
            Files.createDirectory(ignoredSub);
            Files.writeString(ignoredSub.resolve(".ignore"), "");
            Files.writeString(ignoredSub.resolve("hidden.epub"), "content");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            Thread.sleep(8000);

            // Only good.epub should be processed, not hidden.epub
            verify(bookFileTransactionalHandler, times(1)).handleNewBookFile(eq(1L), any());
        }

        @Test
        void bookPerFolderMode_emptyFolder_doesNothing() throws Exception {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

            Path folder = tempDir.resolve("empty");
            Files.createDirectory(folder);

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, folder, true);

            Thread.sleep(8000);

            verify(libraryProcessingService, never()).processLibraryFiles(any(), any());
            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }

        @Test
        void pathOutsideLibrary_skipped() throws Exception {
            Path outsideFolder = Files.createTempDirectory("outside");
            try {
                Files.writeString(outsideFolder.resolve("book.epub"), "content");

                processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, outsideFolder, true);

                Thread.sleep(8000);

                // handleEvent should skip because path is outside library
                verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
            } finally {
                try (var stream = Files.walk(outsideFolder)) {
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
        }
    }

    @Nested
    class FolderDeleteHandling {

        @Test
        void folderWithBooks_addsToPool() throws Exception {
            BookEntity book = BookEntity.builder()
                    .id(10L)
                    .library(library)
                    .libraryPath(libraryPath)
                    .deleted(false)
                    .bookFiles(new ArrayList<>(List.of(BookFileEntity.builder()
                            .id(100L)
                            .fileName("test.epub")
                            .fileSubPath("books")
                            .currentHash("hash")
                            .isBookFormat(true)
                            .bookType(BookFileType.EPUB)
                            .build())))
                    .build();

            when(bookRepository.findBooksWithFilesUnderPath(eq(1L), anyString()))
                    .thenReturn(List.of(book));

            Path folder = tempDir.resolve("books");

            processor.processEvent(StandardWatchEventKinds.ENTRY_DELETE, 1L, folder, true);

            Thread.sleep(2000);

            verify(pendingDeletionPool).addFolderDeletion(any(), eq(1L), eq(List.of(book)), any());
        }

        @Test
        void folderWithNoBooks_doesNotAddToPool() throws Exception {
            when(bookRepository.findBooksWithFilesUnderPath(eq(1L), anyString()))
                    .thenReturn(List.of());

            Path folder = tempDir.resolve("emptybooks");

            processor.processEvent(StandardWatchEventKinds.ENTRY_DELETE, 1L, folder, true);

            Thread.sleep(2000);

            verify(pendingDeletionPool, never()).addFolderDeletion(any(), anyLong(), any(), any());
        }
    }

    @Nested
    class FileDeleteHandling {

        @Test
        void bookFileFound_addsToPool() throws Exception {
            BookEntity book = BookEntity.builder()
                    .id(10L).library(library).libraryPath(libraryPath).deleted(false)
                    .bookFiles(new ArrayList<>()).build();
            BookFileEntity bookFile = BookFileEntity.builder()
                    .id(100L).book(book).fileName("test.epub").fileSubPath("sub")
                    .currentHash("hash").isBookFormat(true).bookType(BookFileType.EPUB).build();

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(eq(1L), anyString(), eq("test.epub")))
                    .thenReturn(Optional.of(bookFile));

            Path file = tempDir.resolve("sub").resolve("test.epub");

            processor.processEvent(StandardWatchEventKinds.ENTRY_DELETE, 1L, file, false);

            Thread.sleep(2000);

            verify(pendingDeletionPool).addFileDeletion(any(), eq(1L), eq(bookFile), eq(book), any());
        }

        @Test
        void bookFileNotFound_logsAndContinues() throws Exception {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            Path file = tempDir.resolve("sub").resolve("missing.epub");

            processor.processEvent(StandardWatchEventKinds.ENTRY_DELETE, 1L, file, false);

            Thread.sleep(2000);

            verify(pendingDeletionPool, never()).addFileDeletion(any(), anyLong(), any(), any(), any());
        }
    }

    @Nested
    class FileCreateHandling {

        @Test
        void zeroByteFile_skipped() throws Exception {
            Path file = tempDir.resolve("empty.epub");
            Files.createFile(file);

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, file, false);

            // Wait for stability check + processing
            Thread.sleep(4000);

            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }

        @Test
        void nonBookFile_skipped() throws Exception {
            Path file = tempDir.resolve("readme.txt");
            Files.writeString(file, "hello");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, file, false);

            Thread.sleep(4000);

            verify(bookFileTransactionalHandler, never()).handleNewBookFile(anyLong(), any());
        }

        @Test
        void validBookFile_processedAfterStabilityCheck() throws Exception {
            Path file = tempDir.resolve("book.epub");
            Files.writeString(file, "book content");

            processor.processEvent(StandardWatchEventKinds.ENTRY_CREATE, 1L, file, false);

            // Stability check needs file to be stable for STABILITY_CHECK_INTERVAL_MS (3s)
            // then event goes to queue and is processed by virtual thread
            Thread.sleep(7000);

            verify(bookFileTransactionalHandler).handleNewBookFile(eq(1L), any());
        }
    }

    @Nested
    class Shutdown {

        @Test
        void stopCompletesCleanly() {
            // Just verify stop doesn't throw
            processor.stop();

            // Reinitialize so tearDown's shutdown doesn't fail
            processor = new LibraryFileEventProcessor(
                    libraryRepository, bookRepository, bookFileTransactionalHandler,
                    bookFilePersistenceService, libraryProcessingService, libraryScanListener, pendingDeletionPool);
        }
    }
}
