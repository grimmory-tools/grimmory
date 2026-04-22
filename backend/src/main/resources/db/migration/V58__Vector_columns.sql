ALTER TABLE book_metadata ADD COLUMN IF NOT EXISTS embedding_vector TEXT;
ALTER TABLE book_metadata ADD COLUMN IF NOT EXISTS embedding_updated_at DATETIME;