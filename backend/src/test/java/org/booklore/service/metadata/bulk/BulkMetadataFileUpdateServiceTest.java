package org.booklore.service.metadata.bulk;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.file.BookFileMutationGuard;
import org.booklore.service.metadata.writer.EpubMetadataWriter;
import org.booklore.service.metadata.writer.NasSynologyPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BulkMetadataFileUpdateServiceTest {
    @TempDir
    Path directory;

    @Test
    void applyCommitsDatabaseThenPreparesAndInstallsJournaledEpub() throws Exception {
        Path source = Files.writeString(directory.resolve("book.epub"), "epub");
        BookEntity book = book(source);
        BulkMetadataJournal journal = mock(BulkMetadataJournal.class);
        BulkMetadataDatabaseService database = mock(BulkMetadataDatabaseService.class);
        BulkMetadataPreparationService preparation = mock(BulkMetadataPreparationService.class);
        BulkMetadataEpubInstaller installer = mock(BulkMetadataEpubInstaller.class);
        BookRepository books = mock(BookRepository.class);
        BookFileRepository files = mock(BookFileRepository.class);
        EpubMetadataWriter writer = mock(EpubMetadataWriter.class);
        BookFileMutationGuard guard = mock(BookFileMutationGuard.class);
        when(journal.enqueue("job-1", 7L)).thenReturn("00000000-0000-0000-0000-000000000007");
        when(books.findAllWithMetadataByIds(Set.of(7L))).thenReturn(List.of(book));
        when(preparation.prepare(anyString(), any(), same(book.getMetadata()), any())).thenReturn("c".repeat(64));
        when(installer.install(anyString())).thenReturn(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);
        when(files.updateFingerprintAndFileSize(eq(11L), anyString(), eq(1L))).thenReturn(1);
        var service = new BulkMetadataFileUpdateService(
                journal, database, preparation, installer, books, files, writer, guard,
                permissions());
        BookMetadata fetched = BookMetadata.builder().title("Updated").build();

        service.apply("job-1", 7L, fetched, MetadataReplaceMode.REPLACE_MISSING, false);

        verify(database).apply("00000000-0000-0000-0000-000000000007", 7L, fetched,
                MetadataReplaceMode.REPLACE_MISSING, false);
        verify(preparation).prepare(eq("00000000-0000-0000-0000-000000000007"),
                argThat(intent -> intent.originalSha256().matches("[0-9a-f]{64}")
                        && intent.source().equals(source.toAbsolutePath().normalize())
                        && intent.candidate().getParent().equals(source.getParent())
                        && intent.backup().getParent().equals(source.getParent())),
                same(book.getMetadata()), any());
        verify(installer).install("00000000-0000-0000-0000-000000000007");
        verify(journal).releaseApplied("00000000-0000-0000-0000-000000000007");
        verify(guard).beforeMutation();
    }

    @Test
    void resumeInstallsPreparedEpubAndRefreshesFingerprintWithoutRepeatingDatabaseUpdate() throws Exception {
        Path source = Files.writeString(directory.resolve("prepared.epub"), "prepared");
        BookEntity book = book(source);
        BulkMetadataJournal journal = mock(BulkMetadataJournal.class);
        BulkMetadataDatabaseService database = mock(BulkMetadataDatabaseService.class);
        BulkMetadataPreparationService preparation = mock(BulkMetadataPreparationService.class);
        BulkMetadataEpubInstaller installer = mock(BulkMetadataEpubInstaller.class);
        BookRepository books = mock(BookRepository.class);
        BookFileRepository files = mock(BookFileRepository.class);
        BulkMetadataJournalEntity entry = BulkMetadataJournalEntity.builder()
                .operationId("operation-1")
                .bookId(7L)
                .stage(BulkMetadataJournalEntity.Stage.EPUB_PREPARED)
                .build();
        when(journal.getForRecovery("operation-1")).thenReturn(entry);
        when(books.findAllWithMetadataByIds(Set.of(7L))).thenReturn(List.of(book));
        when(installer.install("operation-1")).thenReturn(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);
        when(files.updateFingerprintAndFileSize(eq(11L), anyString(), eq(1L))).thenReturn(1);
        var service = new BulkMetadataFileUpdateService(
                journal, database, preparation, installer, books, files,
                mock(EpubMetadataWriter.class), mock(BookFileMutationGuard.class), permissions());

        assertThat(service.resume("operation-1")).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_APPLIED);

        verify(database, never()).apply(anyString(), anyLong(), any(), any(), anyBoolean());
        verify(preparation, never()).prepare(anyString(), any(), any(), any());
        verify(installer).install("operation-1");
        verify(journal).releaseApplied("operation-1");
    }

    private static BookEntity book(Path source) {
        BookEntity book = BookEntity.builder().id(7L).build();
        book.setMetadata(BookMetadataEntity.builder().book(book).title("Updated").build());
        book.getMetadata().setAuthors(List.of());
        book.getMetadata().setCategories(Set.of());
        book.getMetadata().setMoods(Set.of());
        book.getMetadata().setTags(Set.of());
        LibraryPathEntity libraryPath = LibraryPathEntity.builder().path(source.getParent().toString()).build();
        book.setLibraryPath(libraryPath);
        BookFileEntity file = BookFileEntity.builder()
                .id(11L)
                .book(book)
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .fileName(source.getFileName().toString())
                .fileSubPath("")
                .build();
        book.setBookFiles(Set.of(file));
        return book;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<NasSynologyPermissions> permissions() {
        ObjectProvider<NasSynologyPermissions> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
