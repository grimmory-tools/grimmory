ALTER TABLE book_marks
    ADD COLUMN color      VARCHAR(7)    DEFAULT NULL,
    ADD COLUMN notes      VARCHAR(2000) DEFAULT NULL,
    ADD COLUMN priority   INTEGER       DEFAULT NULL,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN version    BIGINT    NOT NULL DEFAULT 1;

-- Update existing records
UPDATE book_marks
SET updated_at = created_at
WHERE updated_at IS NULL;

CREATE INDEX idx_bookmark_book_user_priority
    ON book_marks (book_id, user_id, priority, created_at);
