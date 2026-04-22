-- Add is_physical column to book table for physical book support
ALTER TABLE book ADD COLUMN is_physical BOOLEAN NOT NULL DEFAULT FALSE;

-- Make library_path_id nullable for physical books (they don't have a file path)
ALTER TABLE book MODIFY COLUMN library_path_id BIGINT NULL;

-- Add index for physical books
CREATE INDEX idx_book_is_physical ON book(is_physical);
