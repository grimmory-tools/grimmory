-- Add numeric hardcover_book_id column to book_metadata table
-- This stores the numeric Hardcover book ID for API operations,
-- while the existing hardcover_id column stores the slug for URL linking.

ALTER TABLE book_metadata ADD COLUMN hardcover_book_id INTEGER;

