package org.booklore.service.metadata.bulk;

import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.model.entity.BulkMetadataJournalEntity.Stage;
import org.booklore.service.metadata.writer.EpubFileAttributes;
import org.booklore.service.metadata.writer.NasSynologyPermissions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class BulkMetadataEpubInstaller {
    private final BulkMetadataJournal journal;
    private final NasSynologyPermissions synologyPermissions;

    public BulkMetadataEpubInstaller(BulkMetadataJournal journal,
                                    ObjectProvider<NasSynologyPermissions> synologyPermissions) {
        this.journal = journal;
        this.synologyPermissions = synologyPermissions.getIfAvailable();
    }

    public Stage install(String operationId) throws IOException {
        BulkMetadataJournalEntity entry = journal.getForRecovery(operationId);
        BulkMetadataRecoveryInspector.Inspection inspection = BulkMetadataRecoveryInspector.inspect(entry);
        if (entry.getStage() == Stage.EPUB_APPLYING) {
            if (inspection.observation() == BulkMetadataRecoveryInspector.Observation.CANDIDATE_PRESENT) {
                EpubFileAttributes.Snapshot backupAttributes =
                        EpubFileAttributes.capture(Path.of(entry.getBackupPath()), synologyPermissions);
                EpubFileAttributes.Snapshot sourceAttributes =
                        EpubFileAttributes.capture(Path.of(entry.getSourcePath()), synologyPermissions);
                if (!backupAttributes.equals(sourceAttributes)) {
                    throw new IOException("Installed EPUB ACL differs from the retained backup");
                }
                journal.recoverEpubApplied(operationId);
                return Stage.EPUB_APPLIED;
            }
            if (inspection.observation() != BulkMetadataRecoveryInspector.Observation.ORIGINAL_PRESENT) {
                throw new IOException("EPUB installation state is uncertain; manual inspection is required");
            }
        } else if (entry.getStage() != Stage.EPUB_PREPARED
                || inspection.observation() != BulkMetadataRecoveryInspector.Observation.ORIGINAL_PRESENT) {
            throw new IOException("EPUB operation is not ready for installation");
        }

        Path source = Path.of(entry.getSourcePath());
        Path candidate = Path.of(entry.getCandidatePath());
        Path backup = Path.of(entry.getBackupPath());
        if (!source.normalize().getParent().equals(candidate.normalize().getParent())
                || !source.normalize().getParent().equals(backup.normalize().getParent())) {
            throw new IOException("EPUB source, candidate and backup must be siblings");
        }
        EpubFileAttributes.Snapshot attributes = EpubFileAttributes.capture(source, synologyPermissions);
        EpubFileAttributes.applyAndVerify(attributes, source, candidate, synologyPermissions);
        String candidateHash = entry.getStage() == Stage.EPUB_PREPARED
                ? journal.markEpubApplyingAfterVerification(operationId)
                : entry.getCandidateSha256();
        try {
            Files.move(candidate, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IOException("Atomic EPUB replacement is not supported on this file system", e);
        }

        BulkMetadataJournalEntity applying = journal.getForRecovery(operationId);
        BulkMetadataRecoveryInspector.Inspection installed = BulkMetadataRecoveryInspector.inspect(applying);
        if (installed.observation() != BulkMetadataRecoveryInspector.Observation.CANDIDATE_PRESENT) {
            throw new IOException("Installed EPUB does not match the journal; backup retained for manual recovery");
        }
        EpubFileAttributes.verify(attributes, source, synologyPermissions);
        journal.recordEpubApplied(operationId, candidateHash);
        return Stage.EPUB_APPLIED;
    }
}
