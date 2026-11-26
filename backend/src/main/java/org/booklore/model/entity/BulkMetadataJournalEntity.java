package org.booklore.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "bulk_metadata_journal",
        uniqueConstraints = @UniqueConstraint(name = "uq_bulk_metadata_active_book", columnNames = "active_book_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMetadataJournalEntity {
    public enum Stage {
        PENDING_DB, DB_COMMITTED, EPUB_PREPARING, EPUB_PREPARED, EPUB_APPLYING, EPUB_APPLIED, NEEDS_ATTENTION, FAILED_SAFE
    }

    @Id
    @Column(name = "operation_id", length = 36)
    private String operationId;

    @Column(name = "job_id", nullable = false, length = 100)
    private String jobId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "active_book_id")
    private Long activeBookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Stage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_stage", length = 32)
    private Stage previousStage;

    @Column(name = "source_path", columnDefinition = "TEXT")
    private String sourcePath;

    @Column(name = "candidate_path", columnDefinition = "TEXT")
    private String candidatePath;

    @Column(name = "backup_path", columnDefinition = "TEXT")
    private String backupPath;

    @Column(name = "original_sha256", length = 64)
    private String originalSha256;

    @Column(name = "candidate_sha256", length = 64)
    private String candidateSha256;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;
}
