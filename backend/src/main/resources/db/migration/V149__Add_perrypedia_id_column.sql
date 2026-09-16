ALTER TABLE book_metadata
    ADD COLUMN perrypedia_id VARCHAR(100),
    ADD COLUMN perrypedia_id_locked BOOLEAN DEFAULT FALSE;
