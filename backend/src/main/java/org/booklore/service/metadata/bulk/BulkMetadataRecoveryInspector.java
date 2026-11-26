package org.booklore.service.metadata.bulk;

import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.model.entity.BulkMetadataJournalEntity.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class BulkMetadataRecoveryInspector {
    public enum FileState { ORIGINAL, CANDIDATE, BOTH, MISSING, DIFFERENT, UNVERIFIED, UNSAFE, UNREADABLE }
    public enum Observation { NOT_PREPARED, PREPARATION_INCOMPLETE, ORIGINAL_PRESENT, CANDIDATE_PRESENT, CONFLICT }

    public record Inspection(Stage stage, FileState source, FileState backup, FileState candidate,
                             Observation observation) {}

    private static final long MAX_EPUB_BYTES = 512L * 1024 * 1024;

    private BulkMetadataRecoveryInspector() {
    }

    public static Inspection inspect(BulkMetadataJournalEntity entry) {
        Objects.requireNonNull(entry);
        Stage stage = entry.getStage() == Stage.NEEDS_ATTENTION ? entry.getPreviousStage() : entry.getStage();
        if (stage == Stage.PENDING_DB || stage == Stage.DB_COMMITTED || stage == Stage.FAILED_SAFE || stage == null) {
            return new Inspection(stage, null, null, null, Observation.NOT_PREPARED);
        }
        FileState source = inspectFile(entry.getSourcePath(), entry.getOriginalSha256(), entry.getCandidateSha256());
        FileState backup = inspectFile(entry.getBackupPath(), entry.getOriginalSha256(), entry.getCandidateSha256());
        FileState candidate = inspectFile(entry.getCandidatePath(), entry.getOriginalSha256(), entry.getCandidateSha256());
        if (stage == Stage.EPUB_PREPARING) {
            boolean expected = entry.getCandidateSha256() == null && source == FileState.ORIGINAL
                    && (backup == FileState.ORIGINAL || backup == FileState.MISSING)
                    && (candidate == FileState.ORIGINAL || candidate == FileState.UNVERIFIED
                    || candidate == FileState.MISSING);
            return new Inspection(stage, source, backup, candidate,
                    expected ? Observation.PREPARATION_INCOMPLETE : Observation.CONFLICT);
        }
        boolean backupValid = backup == FileState.ORIGINAL || backup == FileState.BOTH;
        boolean sourceOriginal = source == FileState.ORIGINAL || source == FileState.BOTH;
        boolean sourceCandidate = source == FileState.CANDIDATE || source == FileState.BOTH;
        boolean candidateValid = candidate == FileState.CANDIDATE || candidate == FileState.BOTH;

        Observation observation = Observation.CONFLICT;
        if (backupValid && stage == Stage.EPUB_PREPARED && sourceOriginal && candidateValid) {
            observation = Observation.ORIGINAL_PRESENT;
        } else if (backupValid && stage == Stage.EPUB_APPLYING && sourceOriginal && candidateValid) {
            observation = Observation.ORIGINAL_PRESENT;
        } else if (backupValid && (stage == Stage.EPUB_APPLYING || stage == Stage.EPUB_APPLIED) && sourceCandidate
                && (candidateValid || candidate == FileState.MISSING)) {
            observation = Observation.CANDIDATE_PRESENT;
        }
        return new Inspection(stage, source, backup, candidate, observation);
    }

    private static FileState inspectFile(String value, String originalHash, String candidateHash) {
        if (value == null || originalHash == null || !originalHash.matches("[0-9a-f]{64}")
                || (candidateHash != null && !candidateHash.matches("[0-9a-f]{64}"))) {
            return FileState.UNSAFE;
        }
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute()) {
                return FileState.UNSAFE;
            }
            BasicFileAttributes before;
            try {
                before = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                return FileState.MISSING;
            }
            if (!before.isRegularFile() || before.size() > MAX_EPUB_BYTES) {
                return FileState.UNSAFE;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(
                    Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS), digest)) {
                byte[] buffer = new byte[8192];
                long count = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > before.size() || count > MAX_EPUB_BYTES) {
                        return FileState.UNSAFE;
                    }
                }
                if (count != before.size()) {
                    return FileState.UNSAFE;
                }
            }
            BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile() || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())) {
                return FileState.UNSAFE;
            }
            String actualHash = HexFormat.of().formatHex(digest.digest());
            boolean original = actualHash.equals(originalHash);
            boolean candidate = actualHash.equals(candidateHash);
            if (original && candidate) return FileState.BOTH;
            if (original) return FileState.ORIGINAL;
            if (candidate) return FileState.CANDIDATE;
            if (candidateHash == null) return FileState.UNVERIFIED;
            return FileState.DIFFERENT;
        } catch (IOException | InvalidPathException | SecurityException e) {
            return FileState.UNREADABLE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
