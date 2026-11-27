package org.booklore.service.metadata.bulk;

import lombok.AllArgsConstructor;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.file.BookFileMutationGuard;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.metadata.writer.EpubArchiveIntegrity;
import org.booklore.service.metadata.writer.EpubMetadataWriter;
import org.booklore.service.metadata.writer.NasSynologyPermissions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@AllArgsConstructor
public class BulkMetadataFileUpdateService {
    private final BulkMetadataJournal journal;
    private final BulkMetadataDatabaseService databaseService;
    private final BulkMetadataPreparationService preparationService;
    private final BulkMetadataEpubInstaller installer;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final EpubMetadataWriter epubWriter;
    private final BookFileMutationGuard fileMutationGuard;
    private final ObjectProvider<NasSynologyPermissions> synologyPermissions;

    @Transactional(readOnly = true)
    public boolean supportsSafeEpubUpdate(long bookId) {
        BookEntity book = bookRepository.findAllWithMetadataByIds(java.util.Collections.singleton(bookId))
                .stream().findFirst().orElse(null);
        if (book == null || book.getPrimaryBookFile() == null
                || book.getPrimaryBookFile().getBookType() != BookFileType.EPUB) {
            return false;
        }
        Path source = book.getPrimaryBookFile().getFullFilePath();
        if (!Files.isRegularFile(source) || !epubWriter.shouldSaveMetadataToFile(source.toFile())) {
            return false;
        }
        try {
            String fileSystem = Files.getFileStore(source).type().toLowerCase(java.util.Locale.ROOT);
            boolean networkFileSystem = fileSystem.contains("nfs") || fileSystem.contains("cifs")
                    || fileSystem.contains("smb");
            return !networkFileSystem || synologyPermissions.getIfAvailable() != null;
        } catch (IOException e) {
            return false;
        }
    }

    public void apply(String jobId, long bookId, BookMetadata fetched, MetadataReplaceMode replaceMode,
                      boolean mergeCategories) {
        String operationId = journal.enqueue(jobId, bookId);
        try {
            databaseService.apply(operationId, bookId, fetched, replaceMode, mergeCategories);
        } catch (RuntimeException e) {
            journal.recordSafeFailure(operationId, "DB_UPDATE_FAILED");
            throw e;
        }

        prepareAndInstall(operationId, bookId);
    }

    public BulkMetadataJournalEntity.Stage resume(String operationId) throws IOException {
        BulkMetadataJournalEntity entry = journal.getForRecovery(operationId);
        return switch (entry.getStage()) {
            case PENDING_DB -> throw new IllegalStateException(
                    "Database update must be completed before EPUB recovery");
            case DB_COMMITTED -> prepareAndInstall(operationId, entry.getBookId());
            case EPUB_PREPARED, EPUB_APPLYING -> {
                installer.install(operationId);
                updateInstalledFingerprint(entry.getBookId());
                journal.releaseApplied(operationId);
                yield BulkMetadataJournalEntity.Stage.EPUB_APPLIED;
            }
            case EPUB_APPLIED -> {
                updateInstalledFingerprint(entry.getBookId());
                journal.releaseApplied(operationId);
                yield BulkMetadataJournalEntity.Stage.EPUB_APPLIED;
            }
            case EPUB_PREPARING, NEEDS_ATTENTION, FAILED_SAFE -> throw new IllegalStateException(
                    "EPUB operation requires manual inspection before recovery");
        };
    }

    private BulkMetadataJournalEntity.Stage prepareAndInstall(String operationId, long bookId) {
        try {
            fileMutationGuard.beforeMutation();
            PreparedEpub prepared = loadPreparedEpub(bookId);
            Path parent = prepared.source().getParent();
            // Keep operation files hidden and extensionless: library watchers must not
            // mistake a byte-for-byte backup for a renamed book during preparation.
            String token = ".grimmory-" + operationId;
            Path candidate = parent.resolve(token + ".candidate");
            Path backup = parent.resolve(token + ".backup");
            String sourceHash = EpubArchiveIntegrity.sha256(prepared.source());
            var intent = new BulkMetadataJournal.EpubPreparationIntent(
                    prepared.source(), candidate, backup, sourceHash);

            preparationService.prepare(operationId, intent, prepared.metadata(), new MetadataClearFlags());
            if (installer.install(operationId) != BulkMetadataJournalEntity.Stage.EPUB_APPLIED) {
                throw new IOException("EPUB installation did not reach the applied stage");
            }
            updateInstalledFingerprint(prepared);
            journal.releaseApplied(operationId);
            return BulkMetadataJournalEntity.Stage.EPUB_APPLIED;
        } catch (IOException | RuntimeException e) {
            BulkMetadataJournalEntity entry = journal.getForRecovery(operationId);
            if (entry.getStage() != BulkMetadataJournalEntity.Stage.EPUB_APPLIED) {
                journal.requireAttention(operationId, "EPUB_UPDATE_FAILED");
            } else {
                journal.requireAttention(operationId, "FINGERPRINT_UPDATE_FAILED");
            }
            if (e instanceof IOException io) {
                throw new IllegalStateException("Failed to install refreshed EPUB metadata", io);
            }
            throw (RuntimeException) e;
        }
    }

    private void updateInstalledFingerprint(long bookId) {
        updateInstalledFingerprint(loadPreparedEpub(bookId));
    }

    private void updateInstalledFingerprint(PreparedEpub prepared) {
        try {
            long sizeKb = (Files.size(prepared.source()) + 1023) / 1024;
            int updated = bookFileRepository.updateFingerprintAndFileSize(
                    prepared.bookFileId(), FileFingerprint.generateHash(prepared.source()), sizeKb);
            if (updated != 1) {
                throw new IllegalStateException("Could not update the installed EPUB fingerprint");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not fingerprint the installed EPUB", e);
        }
    }

    @Transactional(readOnly = true)
    public PreparedEpub loadPreparedEpub(long bookId) {
        BookEntity book = bookRepository.findByIdForBulkMetadataEpubUpdate(bookId)
                .orElseThrow(() -> new IllegalStateException("Book disappeared during EPUB update"));
        if (book.getPrimaryBookFile() == null || book.getPrimaryBookFile().getBookType() != BookFileType.EPUB) {
            throw new IllegalStateException("Bulk metadata update no longer targets an EPUB");
        }
        BookMetadataEntity metadata = book.getMetadata();
        metadata.getAuthors().size();
        metadata.getCategories().size();
        metadata.getMoods().size();
        metadata.getTags().size();
        return new PreparedEpub(book.getPrimaryBookFile().getId(),
                book.getPrimaryBookFile().getFullFilePath().normalize().toAbsolutePath(), metadata);
    }

    public record PreparedEpub(long bookFileId, Path source, BookMetadataEntity metadata) {}
}
