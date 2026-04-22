package org.booklore.service.watcher;

import org.booklore.mapper.BookMapper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingDeletionPoolTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private NotificationService notificationService;
    @Mock private BookMapper bookMapper;

    @InjectMocks
    private PendingDeletionPool pool;

    private BookFileEntity bookFile;
    private BookEntity book;
    private LibraryPathEntity libraryPath;

    @BeforeEach
    void setUp() {
        libraryPath = new LibraryPathEntity();
        libraryPath.setId(1L);
        libraryPath.setPath("/library");

        book = BookEntity.builder()
                .id(10L)
                .libraryPath(libraryPath)
                .deleted(false)
                .build();

        bookFile = BookFileEntity.builder()
                .id(100L)
                .book(book)
                .fileName("test.epub")
                .fileSubPath("subfolder")
                .currentHash("abc123")
                .isBookFormat(true)
                .folderBased(false)
                .bookType(BookFileType.EPUB)
                .build();
    }

    @Test
    void matchByHash_findsFileDeletion() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        Optional<PendingDeletionPool.MatchResult> result = pool.matchByHash("abc123");

        assertThat(result).isPresent();
        assertThat(result.get().book().bookId()).isEqualTo(10L);
        assertThat(result.get().file().bookFileId()).isEqualTo(100L);
        verify(timer).cancel(false);
    }

    @Test
    void matchByHash_returnsEmptyForUnknownHash() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        Optional<PendingDeletionPool.MatchResult> result = pool.matchByHash("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void matchByHash_consumesHashOnlyOnce() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        pool.matchByHash("abc123");
        Optional<PendingDeletionPool.MatchResult> second = pool.matchByHash("abc123");

        assertThat(second).isEmpty();
    }

    @Test
    void addFolderDeletion_indexesAllFileHashes() {
        BookFileEntity file2 = BookFileEntity.builder()
                .id(101L).book(book).fileName("test2.epub").fileSubPath("subfolder")
                .currentHash("def456").isBookFormat(true).folderBased(false).bookType(BookFileType.EPUB).build();
        book.setBookFiles(List.of(bookFile, file2));

        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFolderDeletion(Path.of("/library/subfolder"), 1L, List.of(book), timer);

        assertThat(pool.matchByHash("abc123")).isPresent();
        assertThat(pool.matchByHash("def456")).isPresent();
    }

    @Test
    void matchFolderByHashes_matchesWithSufficientOverlap() {
        BookFileEntity file2 = BookFileEntity.builder()
                .id(101L).book(book).fileName("test2.epub").fileSubPath("subfolder")
                .currentHash("def456").isBookFormat(true).folderBased(false).bookType(BookFileType.EPUB).build();
        book.setBookFiles(List.of(bookFile, file2));

        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFolderDeletion(Path.of("/library/subfolder"), 1L, List.of(book), timer);

        Map<Path, String> fileHashes = Map.of(
                Path.of("/new/test.epub"), "abc123",
                Path.of("/new/test2.epub"), "def456");

        Optional<PendingDeletionPool.FolderMatchResult> result = pool.matchFolderByHashes(fileHashes);

        assertThat(result).isPresent();
        assertThat(result.get().hashToFile()).hasSize(2);
        verify(timer).cancel(false);
    }

    @Test
    void matchFolderByHashes_rejectsLowOverlap() {
        book.setBookFiles(List.of(bookFile));

        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFolderDeletion(Path.of("/library/subfolder"), 1L, List.of(book), timer);

        Map<Path, String> fileHashes = Map.of(Path.of("/new/other.epub"), "zzz999");

        Optional<PendingDeletionPool.FolderMatchResult> result = pool.matchFolderByHashes(fileHashes);

        assertThat(result).isEmpty();
    }

    @Test
    void expireFileDeletion_marksLastFileBookAsDeleted() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(bookFileRepository.countByBookId(10L)).thenReturn(1L);

        pool.expireFileDeletion(Path.of("/library/subfolder/test.epub"));

        assertThat(book.getDeleted()).isTrue();
        assertThat(book.getDeletedAt()).isNotNull();
        verify(bookRepository).save(book);
    }

    @Test
    void expireFileDeletion_deletesFileWhenMultipleRemain() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(bookFileRepository.countByBookId(10L)).thenReturn(3L);
        when(bookFileRepository.findById(100L)).thenReturn(Optional.of(bookFile));

        pool.expireFileDeletion(Path.of("/library/subfolder/test.epub"));

        verify(bookFileRepository).delete(bookFile);
        assertThat(book.getDeleted()).isFalse();
    }

    @Test
    void expireFolderDeletion_marksAllBooksDeleted() {
        book.setBookFiles(List.of(bookFile));

        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFolderDeletion(Path.of("/library/subfolder"), 1L, List.of(book), timer);

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

        pool.expireFolderDeletion(Path.of("/library/subfolder"));

        assertThat(book.getDeleted()).isTrue();
        verify(bookRepository).save(book);
    }

    @Test
    void hasPendingForPaths_detectsPendingDeletion() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);

        assertThat(pool.hasPendingForPaths(Set.of(Path.of("/library")))).isTrue();
        assertThat(pool.hasPendingForPaths(Set.of(Path.of("/other")))).isFalse();
    }

    @Test
    void recoverBook_undeletesAndUpdatesPath() {
        ScheduledFuture<?> timer = mock(ScheduledFuture.class);
        pool.addFileDeletion(Path.of("/library/subfolder/test.epub"), 1L, bookFile, book, timer);
        var match = pool.matchByHash("abc123").orElseThrow();

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(bookFileRepository.findById(100L)).thenReturn(Optional.of(bookFile));

        LibraryPathEntity newPath = new LibraryPathEntity();
        newPath.setId(2L);
        newPath.setPath("/new-library");

        pool.recoverBook(match, newPath, "newSub", "renamed.epub", "abc123");

        assertThat(book.getDeleted()).isFalse();
        assertThat(book.getDeletedAt()).isNull();
        assertThat(book.getLibraryPath()).isEqualTo(newPath);
        assertThat(bookFile.getFileSubPath()).isEqualTo("newSub");
        assertThat(bookFile.getFileName()).isEqualTo("renamed.epub");
        verify(bookRepository).save(book);
    }
}
