-- Add LubimyCzytac metadata columns
ALTER TABLE book_metadata
    ADD COLUMN IF NOT EXISTS lubimyczytac_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS lubimyczytac_rating FLOAT,
    ADD COLUMN IF NOT EXISTS lubimyczytac_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS lubimyczytac_rating_locked BOOLEAN DEFAULT FALSE;
