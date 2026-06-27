-- Add OpenLibrary metadata columns
ALTER TABLE book_metadata
    ADD COLUMN IF NOT EXISTS openlibrary_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS openlibrary_id_locked BOOLEAN DEFAULT FALSE;
