package org.booklore.service.watcher;

import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.LibraryOrganizationMode;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.library.LibraryProcessingService;
import org.booklore.util.BookFileGroupingUtils;
import org.booklore.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookFileTransactionalHandlerTest {

    @Mock private BookFilePersistenceService bookFilePersistenceService;
    @Mock private LibraryProcessingService libraryProcessingService;
    @Mock private NotificationService notificationService;
    @Mock private LibraryRepository libraryRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookAdditionalFileRepository bookAdditionalFileRepository;
    @Mock private PendingDeletionPool pendingDeletionPool;

    private BookFileTransactionalHandler handler;

    private AutoCloseable mocks;
    private MockedStatic<FileFingerprint> fingerprintMock;
    private MockedStatic<FileUtils> fileUtilsMock;
    private MockedStatic<BookFileGroupingUtils> groupingMock;

    private LibraryEntity library;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        handler = new BookFileTransactionalHandler(
                bookFilePersistenceService, libraryProcessingService, notificationService,
                libraryRepository, bookRepository, bookAdditionalFileRepository, pendingDeletionPool);

        fingerprintMock = mockStatic(FileFingerprint.class);
        fileUtilsMock = mockStatic(FileUtils.class);
        groupingMock = mockStatic(BookFileGroupingUtils.class);

        fingerprintMock.when(() -> FileFingerprint.generateHash(any(Path.class))).thenReturn("hash123");
        fingerprintMock.when(() -> FileFingerprint.generateFolderHash(any(Path.class))).thenReturn("folderhash");
        fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("sub");
        fileUtilsMock.when(() -> FileUtils.getFileSizeInKb(any(Path.class))).thenReturn(100L);
        fileUtilsMock.when(() -> FileUtils.getFolderSizeInKb(any(Path.class))).thenReturn(500L);
        groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase());
        groupingMock.when(() -> BookFileGroupingUtils.calculateSimilarity(anyString(), anyString())).thenReturn(0.5);

        libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/library");

        library = LibraryEntity.builder()
                .id(1L)
                .name("Test Library")
                .libraryPaths(List.of(libraryPath))
                .organizationMode(LibraryOrganizationMode.AUTO_DETECT)
                .build();

        when(libraryRepository.findById(1L)).thenReturn(Optional.of(library));
        when(bookFilePersistenceService.findMatchingLibraryPath(eq(library), any(Path.class))).thenReturn("/library");
        when(bookFilePersistenceService.getLibraryPathEntityForFile(eq(library), eq("/library"))).thenReturn(libraryPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        groupingMock.close();
        fileUtilsMock.close();
        fingerprintMock.close();
        mocks.close();
    }

    private BookEntity buildBook(Long id, boolean deleted) {
        return BookEntity.builder()
                .id(id)
                .library(library)
                .libraryPath(libraryPath)
                .deleted(deleted)
                .bookFiles(new ArrayList<>())
                .build();
    }

    private BookFileEntity buildBookFile(Long id, BookEntity book, String fileName, String hash) {
        return BookFileEntity.builder()
                .id(id)
                .book(book)
                .fileName(fileName)
                .fileSubPath("sub")
                .currentHash(hash)
                .initialHash(hash)
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .build();
    }

    private PendingDeletionPool.FileSnapshot fileSnap(Long id, String fileName, String hash) {
        return new PendingDeletionPool.FileSnapshot(id, fileName, "sub", hash, false, BookFileType.EPUB);
    }

    private PendingDeletionPool.BookSnapshot bookSnap(Long bookId, List<PendingDeletionPool.FileSnapshot> files) {
        return new PendingDeletionPool.BookSnapshot(bookId, 1L, "sub", files);
    }

    @SuppressWarnings("unchecked")
    private PendingDeletionPool.MatchResult matchResult(PendingDeletionPool.BookSnapshot book, PendingDeletionPool.FileSnapshot file) {
        var pending = new PendingDeletionPool.PendingDeletion(
                Path.of("/library/sub"), false, 1L, Instant.now(),
                mock(ScheduledFuture.class), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        return new PendingDeletionPool.MatchResult(pending, book, file);
    }

    @Nested
    class HandleNewBookFile {

        @Test
        void libraryNotFound_throws() {
            when(libraryRepository.findById(99L)).thenReturn(Optional.empty());

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> handler.handleNewBookFile(99L, Path.of("/library/sub/test.epub")));
        }

        @Test
        void existingAtPath_deletedBook_restoresIt() {
            BookEntity book = buildBook(10L, true);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "oldhash");
            book.setBookFiles(List.of(bookFile));

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(bookFile));
            when(pendingDeletionPool.matchByHash("oldhash")).thenReturn(Optional.empty());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookFilePersistenceService).save(book);
            org.assertj.core.api.Assertions.assertThat(book.getDeleted()).isFalse();
            org.assertj.core.api.Assertions.assertThat(book.getDeletedAt()).isNull();
        }

        @Test
        void existingAtPath_sameHash_skipsProcessing() {
            BookEntity book = buildBook(10L, false);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "hash123");
            book.setBookFiles(List.of(bookFile));

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(bookFile));

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookFilePersistenceService, never()).save(any());
            verify(pendingDeletionPool).cancelByPath(any());
        }

        @Test
        void existingAtPath_hashChanged_updatesHash() {
            BookEntity book = buildBook(10L, false);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "oldhash");
            book.setBookFiles(List.of(bookFile));

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(bookFile));

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(pendingDeletionPool).cancelByPath(any());
            verify(bookFilePersistenceService).save(book);
            org.assertj.core.api.Assertions.assertThat(bookFile.getCurrentHash()).isEqualTo("hash123");
        }

        @Test
        void noExistingAtPath_poolMatch_recoversBook() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());

            var fSnap = fileSnap(100L, "test.epub", "hash123");
            var bSnap = bookSnap(10L, List.of(fSnap));
            var match = matchResult(bSnap, fSnap);
            when(pendingDeletionPool.matchByHash("hash123")).thenReturn(Optional.of(match));

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(pendingDeletionPool).recoverBook(eq(match), eq(libraryPath), eq("sub"), eq("test.epub"), eq("hash123"));
            verify(libraryProcessingService, never()).processLibraryFiles(any(), any());
        }

        @Test
        void noExistingAtPath_hashBasedMoveDetection_updatesPath() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());

            BookEntity existingBook = buildBook(10L, false);
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(eq("hash123"), any()))
                    .thenReturn(Optional.of(existingBook));

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookFilePersistenceService).updatePathIfChanged(eq(existingBook), eq(library), any(), eq("hash123"));
            verify(libraryProcessingService, never()).processLibraryFiles(any(), any());
        }

        @Test
        void autoDetect_fuzzyMatchesFilelessBook() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());

            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(10L).build();
            // Use reflection or setter to set title
            metadata.setTitle("test");
            BookEntity filelessBook = buildBook(10L, false);
            filelessBook.setMetadata(metadata);
            filelessBook.setLibraryPath(libraryPath);

            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of(filelessBook));
            groupingMock.when(() -> BookFileGroupingUtils.calculateSimilarity(anyString(), anyString())).thenReturn(0.90);

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        }

        @Test
        void autoDetect_noFilelessMatch_matchesExistingBookByGroupingKey() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            BookEntity existingBook = buildBook(20L, false);
            BookFileEntity primaryFile = buildBookFile(200L, existingBook, "test.epub", "otherhash");
            existingBook.setBookFiles(new ArrayList<>(List.of(primaryFile)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of(existingBook));

            // Both files should produce the same grouping key (extension stripped)
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("test.pdf")).thenReturn("test");
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("test.epub")).thenReturn("test");

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.pdf"));

            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        }

        @Test
        void autoDetect_noMatch_createsNewBook() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void bookPerFile_neverAttachesToExistingBooks() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FILE);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookRepository, never()).findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString());
            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void bookPerFolder_attachesToBookInSameFolder() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            BookEntity existingBook = buildBook(20L, false);
            BookFileEntity primaryFile = buildBookFile(200L, existingBook, "existing.epub", "otherhash");
            existingBook.setBookFiles(new ArrayList<>(List.of(primaryFile)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of(existingBook));

            handler.handleNewBookFile(1L, Path.of("/library/sub/new.pdf"));

            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
            verify(libraryProcessingService, never()).processLibraryFiles(any(), any());
        }

        @Test
        void bookPerFolder_noBookInFolder_createsNew() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void bookPerFolder_audiobook_findsNearestAncestorWithEbook() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn("author/book/audio");

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "author/book/audio")).thenReturn(List.of());

            BookEntity parentBook = buildBook(30L, false);
            BookFileEntity ebookFile = buildBookFile(300L, parentBook, "book.epub", "ebookhash");
            ebookFile.setBookType(BookFileType.EPUB);
            parentBook.setBookFiles(new ArrayList<>(List.of(ebookFile)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "author/book")).thenReturn(List.of(parentBook));

            handler.handleNewBookFile(1L, Path.of("/library/author/book/audio/chapter1.m4b"));

            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        }

        @Test
        void bookPerFile_exactFilelessMatch_usesExactNotFuzzy() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FILE);

            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(10L).build();
            metadata.setTitle("different title");
            BookEntity filelessBook = buildBook(10L, false);
            filelessBook.setMetadata(metadata);
            filelessBook.setLibraryPath(libraryPath);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of(filelessBook));
            // extractGroupingKey returns lowercase of input, so "different title" != "test.epub"
            // No exact match -> should create new
            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void autoDetect_filelessBook_wrongLibraryPath_skipped() {
            LibraryPathEntity otherPath = new LibraryPathEntity();
            otherPath.setId(99L);
            otherPath.setPath("/other");

            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(10L).build();
            metadata.setTitle("test");
            BookEntity filelessBook = buildBook(10L, false);
            filelessBook.setMetadata(metadata);
            filelessBook.setLibraryPath(otherPath);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of(filelessBook));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void autoDetect_fuzzyMatchBook_returnsHighSimilarity() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            BookEntity book1 = buildBook(20L, false);
            BookFileEntity file1 = buildBookFile(200L, book1, "mybook.epub", "h1");
            book1.setBookFiles(new ArrayList<>(List.of(file1)));

            BookEntity book2 = buildBook(21L, false);
            BookFileEntity file2 = buildBookFile(201L, book2, "otherbook.epub", "h2");
            book2.setBookFiles(new ArrayList<>(List.of(file2)));

            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of(book1, book2));
            // Make groupingKey different to avoid exact match, but similarity high for book1
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("mybook.pdf")).thenReturn("mybook");
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("mybook.epub")).thenReturn("mybook");
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("otherbook.epub")).thenReturn("otherbook");
            groupingMock.when(() -> BookFileGroupingUtils.calculateSimilarity("mybook", "mybook")).thenReturn(1.0);
            groupingMock.when(() -> BookFileGroupingUtils.calculateSimilarity("mybook", "otherbook")).thenReturn(0.3);

            handler.handleNewBookFile(1L, Path.of("/library/sub/mybook.pdf"));

            // Should attach to book1 via exact grouping key match
            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        }

        @Test
        void nullSubPath_autoDetect_createsNewBook() {
            fileUtilsMock.when(() -> FileUtils.getRelativeSubPath(anyString(), any(Path.class))).thenReturn(null);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), any(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/test.epub"));

            // findMatchingBook returns null for null subPath, so new book is created
            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void bookPerFolder_multipleActiveBooksInFolder_picksOneWithMostFiles() {
            library.setOrganizationMode(LibraryOrganizationMode.BOOK_PER_FOLDER);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            BookEntity smallBook = buildBook(20L, false);
            BookFileEntity f1 = buildBookFile(200L, smallBook, "a.epub", "h1");
            smallBook.setBookFiles(new ArrayList<>(List.of(f1)));

            BookEntity bigBook = buildBook(21L, false);
            BookFileEntity f2 = buildBookFile(201L, bigBook, "b.epub", "h2");
            BookFileEntity f3 = buildBookFile(202L, bigBook, "b.pdf", "h3");
            bigBook.setBookFiles(new ArrayList<>(List.of(f2, f3)));

            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of(smallBook, bigBook));

            handler.handleNewBookFile(1L, Path.of("/library/sub/new.mobi"));

            // Should attach to bigBook (more files)
            verify(bookAdditionalFileRepository).save(argThat(bf -> bf.getBook().equals(bigBook)));
        }

        @Test
        void existingAtPath_deletedBook_matchesPendingDeletionHash() {
            BookEntity book = buildBook(10L, true);
            BookFileEntity bookFile = buildBookFile(100L, book, "test.epub", "oldhash");
            book.setBookFiles(List.of(bookFile));

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(1L, "sub", "test.epub"))
                    .thenReturn(Optional.of(bookFile));
            when(pendingDeletionPool.matchByHash("oldhash")).thenReturn(Optional.empty());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(pendingDeletionPool).matchByHash("oldhash");
            verify(bookFilePersistenceService).save(book);
        }

        @Test
        void nullOrganizationMode_defaultsToAutoDetect() {
            library.setOrganizationMode(null);

            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of());

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            // AUTO_DETECT path: findFilelessBooksByLibraryId is called (fuzzy matching)
            verify(bookRepository).findFilelessBooksByLibraryId(1L);
        }

        @Test
        void autoDetect_deletedBookInDirectory_skipped() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());
            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of());

            BookEntity deletedBook = buildBook(20L, true);
            BookFileEntity file = buildBookFile(200L, deletedBook, "test.epub", "h1");
            deletedBook.setBookFiles(new ArrayList<>(List.of(file)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(1L, "sub")).thenReturn(List.of(deletedBook));

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.pdf"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }

        @Test
        void filelessBook_nullLibraryPath_setsLibraryPath() {
            when(bookFilePersistenceService.findBookFileByLibraryPathSubPathAndFileName(anyLong(), anyString(), anyString()))
                    .thenReturn(Optional.empty());
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findByCurrentHashIncludingRecentlyDeleted(anyString(), any())).thenReturn(Optional.empty());

            BookMetadataEntity metadata = BookMetadataEntity.builder().bookId(10L).build();
            metadata.setTitle("test");
            BookEntity filelessBook = buildBook(10L, false);
            filelessBook.setMetadata(metadata);
            filelessBook.setLibraryPath(null);

            when(bookRepository.findFilelessBooksByLibraryId(1L)).thenReturn(List.of(filelessBook));
            groupingMock.when(() -> BookFileGroupingUtils.calculateSimilarity(anyString(), anyString())).thenReturn(0.90);

            handler.handleNewBookFile(1L, Path.of("/library/sub/test.epub"));

            verify(bookRepository).save(argThat(b -> b.getLibraryPath() != null));
            verify(bookAdditionalFileRepository).save(any(BookFileEntity.class));
        }
    }

    @Nested
    class HandleNewFolderAudiobook {

        @Test
        void libraryNotFound_throws() {
            when(libraryRepository.findById(99L)).thenReturn(Optional.empty());

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> handler.handleNewFolderAudiobook(99L, Path.of("/library/audiobook")));
        }

        @Test
        void poolMatch_recoversBook() {
            var fSnap = fileSnap(100L, "audiobook", "folderhash");
            var bSnap = bookSnap(10L, List.of(fSnap));
            var match = matchResult(bSnap, fSnap);
            when(pendingDeletionPool.matchByHash("folderhash")).thenReturn(Optional.of(match));

            handler.handleNewFolderAudiobook(1L, Path.of("/library/sub/audiobook"));

            verify(pendingDeletionPool).recoverBook(eq(match), eq(libraryPath), eq("sub"), eq("audiobook"), eq("folderhash"));
            verify(libraryProcessingService, never()).processLibraryFiles(any(), any());
        }

        @Test
        void matchingBook_autoAttachesFolderAudiobook() {
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());

            BookEntity existingBook = buildBook(20L, false);
            BookFileEntity primaryFile = buildBookFile(200L, existingBook, "audiobook.epub", "h1");
            existingBook.setBookFiles(new ArrayList<>(List.of(primaryFile)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of(existingBook));

            // Folder name "audiobook" and file "audiobook.epub" should produce same grouping key
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("audiobook")).thenReturn("audiobook");
            groupingMock.when(() -> BookFileGroupingUtils.extractGroupingKey("audiobook.epub")).thenReturn("audiobook");

            handler.handleNewFolderAudiobook(1L, Path.of("/library/sub/audiobook"));

            verify(bookAdditionalFileRepository).save(argThat(bf ->
                    bf.isFolderBased() && bf.getBookType() == BookFileType.AUDIOBOOK));
        }

        @Test
        void noMatch_createsNewFolderAudiobook() {
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of());

            handler.handleNewFolderAudiobook(1L, Path.of("/library/sub/audiobook"));

            verify(libraryProcessingService).processLibraryFiles(argThat(files ->
                    files.size() == 1 && files.getFirst().isFolderBased() && files.getFirst().getBookFileType() == BookFileType.AUDIOBOOK), eq(library));
        }

        @Test
        void deletedBookInParent_notMatchedForFolderAudiobook() {
            when(pendingDeletionPool.matchByHash(anyString())).thenReturn(Optional.empty());

            BookEntity deletedBook = buildBook(20L, true);
            BookFileEntity file = buildBookFile(200L, deletedBook, "audiobook.epub", "h1");
            deletedBook.setBookFiles(new ArrayList<>(List.of(file)));
            when(bookRepository.findAllByLibraryPathIdAndFileSubPath(anyLong(), anyString())).thenReturn(List.of(deletedBook));

            handler.handleNewFolderAudiobook(1L, Path.of("/library/sub/audiobook"));

            verify(libraryProcessingService).processLibraryFiles(anyList(), eq(library));
        }
    }
}
