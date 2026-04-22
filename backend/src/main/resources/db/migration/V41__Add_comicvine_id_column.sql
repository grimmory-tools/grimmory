ALTER TABLE book_metadata
    ADD COLUMN comicvine_id VARCHAR(100),
    ADD COLUMN comicvine_id_locked BOOLEAN DEFAULT FALSE;