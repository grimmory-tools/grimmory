CREATE TABLE IF NOT EXISTS bulk_metadata_journal (
    operation_id VARCHAR(36) NOT NULL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL,
    book_id BIGINT NOT NULL,
    active_book_id BIGINT DEFAULT NULL,
    stage VARCHAR(32) NOT NULL,
    previous_stage VARCHAR(32) DEFAULT NULL,
    source_path TEXT DEFAULT NULL,
    candidate_path TEXT DEFAULT NULL,
    backup_path TEXT DEFAULT NULL,
    original_sha256 VARCHAR(64) DEFAULT NULL,
    candidate_sha256 VARCHAR(64) DEFAULT NULL,
    error_code VARCHAR(64) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_bulk_metadata_active_book UNIQUE (active_book_id)
);

CREATE INDEX IF NOT EXISTS idx_bulk_metadata_journal_job ON bulk_metadata_journal (job_id, operation_id);
CREATE INDEX IF NOT EXISTS idx_bulk_metadata_journal_stage ON bulk_metadata_journal (stage, operation_id);
