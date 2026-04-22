-- Add hardcover_book_id_locked column to book_metadata table

ALTER TABLE book_metadata ADD COLUMN hardcover_book_id_locked BOOLEAN DEFAULT FALSE;
