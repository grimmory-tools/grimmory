ALTER TABLE book_metadata ADD COLUMN audiobook_cover_updated_on TIMESTAMP;
ALTER TABLE book_metadata ADD COLUMN audible_id VARCHAR(100);
ALTER TABLE book_metadata ADD COLUMN audible_rating FLOAT;
ALTER TABLE book_metadata ADD COLUMN audible_review_count INTEGER;
ALTER TABLE book_metadata ADD COLUMN audible_id_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN audible_rating_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN audible_review_count_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN audiobook_cover_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN narrator_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN abridged_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE book_metadata ADD COLUMN narrator VARCHAR(500);
ALTER TABLE book_metadata ADD COLUMN abridged BOOLEAN;

ALTER TABLE book ADD COLUMN audiobook_cover_hash VARCHAR(20);

-- Add allowed_formats column to library table
-- Stores JSON array of allowed BookFileType values (e.g., ["EPUB", "PDF", "AUDIOBOOK"])
-- NULL means all formats are allowed (backward compatible)
ALTER TABLE library ADD COLUMN allowed_formats TEXT;

ALTER TABLE book_file ADD COLUMN duration_seconds BIGINT;
ALTER TABLE book_file ADD COLUMN bitrate INTEGER;
ALTER TABLE book_file ADD COLUMN sample_rate INTEGER;
ALTER TABLE book_file ADD COLUMN channels INTEGER;
ALTER TABLE book_file ADD COLUMN codec VARCHAR(50);
ALTER TABLE book_file ADD COLUMN chapter_count INTEGER;
ALTER TABLE book_file ADD COLUMN chapters_json TEXT;
