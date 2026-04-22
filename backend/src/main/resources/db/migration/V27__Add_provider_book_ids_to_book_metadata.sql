ALTER TABLE book_metadata
    ADD COLUMN goodreads_id VARCHAR(100),
    ADD COLUMN hardcover_id VARCHAR(100),
    ADD COLUMN google_id VARCHAR(100),
    ADD COLUMN goodreads_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN hardcover_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN google_id_locked BOOLEAN DEFAULT FALSE;