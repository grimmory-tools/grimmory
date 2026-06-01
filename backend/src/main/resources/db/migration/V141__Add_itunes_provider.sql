ALTER TABLE book_metadata
    ADD COLUMN itunes_id VARCHAR(100),
    ADD COLUMN itunes_rating DOUBLE PRECISION,
    ADD COLUMN itunes_review_count INTEGER,
    ADD COLUMN itunes_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN itunes_rating_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN itunes_review_count_locked BOOLEAN DEFAULT FALSE;
