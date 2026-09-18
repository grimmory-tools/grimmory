-- Add AppleBooks metadata columns
ALTER TABLE book_metadata
    ADD COLUMN IF NOT EXISTS applebooks_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS applebooks_rating FLOAT,
    ADD COLUMN IF NOT EXISTS applebooks_review_count INT,
    ADD COLUMN IF NOT EXISTS applebooks_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS applebooks_rating_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS applebooks_review_count_locked BOOLEAN DEFAULT FALSE;
