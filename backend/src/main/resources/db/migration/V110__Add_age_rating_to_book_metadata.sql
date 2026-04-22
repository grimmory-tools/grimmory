-- Add age rating fields to book_metadata table for content restriction filtering
ALTER TABLE book_metadata ADD COLUMN age_rating INT DEFAULT NULL;
ALTER TABLE book_metadata ADD COLUMN content_rating VARCHAR(20) DEFAULT NULL;
ALTER TABLE book_metadata ADD COLUMN age_rating_locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN content_rating_locked BOOLEAN NOT NULL DEFAULT FALSE;
