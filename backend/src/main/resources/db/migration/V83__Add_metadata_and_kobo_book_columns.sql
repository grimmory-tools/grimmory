ALTER TABLE book
    ADD COLUMN IF NOT EXISTS metadata_updated_at TIMESTAMP;

ALTER TABLE book
    ADD COLUMN IF NOT EXISTS metadata_for_write_updated_at TIMESTAMP;

ALTER TABLE kobo_library_snapshot_book
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(255);

ALTER TABLE kobo_library_snapshot_book
    ADD COLUMN IF NOT EXISTS metadata_updated_at TIMESTAMP;

ALTER TABLE book
    ADD COLUMN IF NOT EXISTS book_cover_hash VARCHAR(20);

