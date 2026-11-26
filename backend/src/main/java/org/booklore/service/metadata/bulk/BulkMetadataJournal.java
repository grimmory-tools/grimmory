package org.booklore.service.metadata.bulk;

import lombok.AllArgsConstructor;
import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.model.entity.BulkMetadataJournalEntity.Stage;
import org.booklore.repository.BulkMetadataJournalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@AllArgsConstructor
public class BulkMetadataJournal {
    private final BulkMetadataJournalRepository repository;

    public record EpubPreparationIntent(Path source, Path candidate, Path backup, String originalSha256) {}

    @Transactional
    public String enqueue(String jobId, long bookId) {
        String operationId = UUID.randomUUID().toString();
        reserve(operationId, jobId, bookId);
        return operationId;
    }

    @Transactional
    public Stage reserve(String operationId, String jobId, long bookId) {
        if (!validOperationId(operationId)) {
            throw new IllegalArgumentException("Invalid bulk metadata operation ID");
        }
        if (jobId == null || jobId.isBlank() || jobId.length() > 100 || bookId <= 0) {
            throw new IllegalArgumentException("Invalid bulk metadata job or book");
        }
        var existing = repository.findForUpdate(operationId);
        if (existing.isPresent()) {
            BulkMetadataJournalEntity entry = existing.get();
            if (!jobId.equals(entry.getJobId()) || !Objects.equals(bookId, entry.getBookId())) {
                throw new IllegalArgumentException("Bulk metadata operation ID belongs to another job or book");
            }
            return entry.getStage();
        }
        Instant now = Instant.now();
        repository.saveAndFlush(BulkMetadataJournalEntity.builder()
                .operationId(operationId)
                .jobId(jobId)
                .bookId(bookId)
                .activeBookId(bookId)
                .stage(Stage.PENDING_DB)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return Stage.PENDING_DB;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDatabaseCommit(String operationId) {
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.PENDING_DB);
        advance(entry, Stage.DB_COMMITTED);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPendingDatabase(String operationId, long bookId) {
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.PENDING_DB);
        if (!Objects.equals(entry.getBookId(), bookId) || !Objects.equals(entry.getActiveBookId(), bookId)) {
            throw new IllegalArgumentException("Bulk metadata operation does not reserve this book");
        }
    }

    @Transactional
    public void recordEpubPreparationIntent(String operationId, EpubPreparationIntent preparation) {
        validatePreparationIntent(preparation);
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.DB_COMMITTED);
        entry.setSourcePath(preparation.source().normalize().toString());
        entry.setCandidatePath(preparation.candidate().normalize().toString());
        entry.setBackupPath(preparation.backup().normalize().toString());
        entry.setOriginalSha256(preparation.originalSha256());
        advance(entry, Stage.EPUB_PREPARING);
    }

    @Transactional
    public void recordEpubPrepared(String operationId, String candidateSha256) {
        if (!validHash(candidateSha256)) {
            throw new IllegalArgumentException("Invalid prepared EPUB hash");
        }
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.EPUB_PREPARING);
        entry.setCandidateSha256(candidateSha256);
        advance(entry, Stage.EPUB_PREPARED);
    }

    @Transactional
    public void markEpubApplying(String operationId) {
        advance(requireStage(operationId, Stage.EPUB_PREPARED), Stage.EPUB_APPLYING);
    }

    @Transactional
    public void recordEpubApplied(String operationId, String installedSha256) {
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.EPUB_APPLYING);
        if (!Objects.equals(entry.getCandidateSha256(), installedSha256)) {
            throw new IllegalArgumentException("Installed EPUB does not match the prepared candidate");
        }
        advance(entry, Stage.EPUB_APPLIED);
    }

    @Transactional(readOnly = true)
    public BulkMetadataJournalEntity getForRecovery(String operationId) {
        return repository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Bulk metadata operation not found"));
    }

    @Transactional
    public String markEpubApplyingAfterVerification(String operationId) throws IOException {
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.EPUB_PREPARED);
        BulkMetadataRecoveryInspector.Inspection inspection = BulkMetadataRecoveryInspector.inspect(entry);
        if (inspection.observation() != BulkMetadataRecoveryInspector.Observation.ORIGINAL_PRESENT) {
            throw new IOException("EPUB files do not match the prepared operation");
        }
        advance(entry, Stage.EPUB_APPLYING);
        return entry.getCandidateSha256();
    }

    @Transactional
    public void recoverEpubApplied(String operationId) throws IOException {
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.EPUB_APPLYING);
        BulkMetadataRecoveryInspector.Inspection inspection = BulkMetadataRecoveryInspector.inspect(entry);
        if (inspection.observation() != BulkMetadataRecoveryInspector.Observation.CANDIDATE_PRESENT) {
            throw new IOException("Installed EPUB does not match the prepared operation");
        }
        advance(entry, Stage.EPUB_APPLIED);
    }

    @Transactional
    public void recordSafeFailure(String operationId, String errorCode) {
        validateErrorCode(errorCode);
        BulkMetadataJournalEntity entry = requireStage(operationId, Stage.PENDING_DB);
        entry.setErrorCode(errorCode);
        entry.setActiveBookId(null);
        advance(entry, Stage.FAILED_SAFE);
    }

    @Transactional
    public void requireAttention(String operationId, String errorCode) {
        validateErrorCode(errorCode);
        BulkMetadataJournalEntity entry = locked(operationId);
        if (entry.getStage() == Stage.FAILED_SAFE || entry.getStage() == Stage.NEEDS_ATTENTION) {
            throw new IllegalStateException("Operation cannot be marked for attention in its current state");
        }
        entry.setPreviousStage(entry.getStage());
        entry.setErrorCode(errorCode);
        advance(entry, Stage.NEEDS_ATTENTION);
    }

    private BulkMetadataJournalEntity requireStage(String operationId, Stage expected) {
        BulkMetadataJournalEntity entry = locked(operationId);
        if (entry.getStage() != expected) {
            throw new IllegalStateException("Bulk metadata operation is not in the expected state");
        }
        return entry;
    }

    private BulkMetadataJournalEntity locked(String operationId) {
        return repository.findForUpdate(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Bulk metadata operation not found"));
    }

    private void advance(BulkMetadataJournalEntity entry, Stage next) {
        entry.setStage(next);
        entry.setUpdatedAt(Instant.now());
        repository.saveAndFlush(entry);
    }

    private static void validatePreparationIntent(EpubPreparationIntent preparation) {
        if (preparation == null || !absolute(preparation.source()) || !absolute(preparation.candidate())
                || !absolute(preparation.backup())
                || preparation.source().normalize().equals(preparation.candidate().normalize())
                || preparation.source().normalize().equals(preparation.backup().normalize())
                || preparation.candidate().normalize().equals(preparation.backup().normalize())
                || !validHash(preparation.originalSha256())) {
            throw new IllegalArgumentException("Invalid EPUB preparation evidence");
        }
    }

    private static boolean absolute(Path path) {
        return path != null && path.isAbsolute();
    }

    private static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static boolean validOperationId(String operationId) {
        if (operationId == null) {
            return false;
        }
        try {
            return UUID.fromString(operationId).toString().equals(operationId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void validateErrorCode(String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Invalid bulk metadata error code");
        }
    }
}
