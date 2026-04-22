-- Migrate file specific data from book to book_file
RENAME TABLE book_additional_file TO book_file_old;
CREATE TABLE book_file LIKE book_file_old;
 ALTER TABLE book_file
 ADD CONSTRAINT fk_book_file_book
 FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
ALTER TABLE book_file ADD COLUMN is_book boolean DEFAULT false;
ALTER TABLE book_file ADD COLUMN book_type varchar(32);
ALTER TABLE book_file ADD COLUMN archive_type VARCHAR(255);

-- Drop the column before importing the data to avoid duplicate index errors
ALTER TABLE book_file DROP COLUMN alt_format_current_hash;

INSERT INTO book_file (book_id, file_name, file_sub_path, is_book, book_type, archive_type, file_size_kb, initial_hash, added_on, current_hash)
SELECT id, file_name, file_sub_path, true, CASE when book_type = 0 then 'PDF' when book_type = 1 then 'EPUB' when book_type = 2 then 'CBX' end, archive_type, file_size_kb, initial_hash, added_on, current_hash FROM book;

INSERT INTO book_file (book_id, file_name, file_sub_path, file_size_kb, initial_hash, current_hash, description, added_on, additional_file_type)
SELECT book_id, file_name, file_sub_path, file_size_kb, initial_hash, current_hash, description, added_on, additional_file_type FROM book_file_old;

UPDATE book_file SET is_book = true WHERE additional_file_type = 'ALTERNATIVE_FORMAT';
ALTER TABLE book_file DROP COLUMN additional_file_type;

-- Set book_type for existing book files
UPDATE book_file
SET book_type = CASE
    WHEN LOWER(file_name) LIKE '%.epub' THEN 'EPUB'
    WHEN LOWER(file_name) LIKE '%.pdf'  THEN 'PDF'
    WHEN LOWER(file_name) LIKE '%.cbz'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.cbr'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.cb7'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.fb2'  THEN 'FB2'
    ELSE book_type
END
WHERE is_book = 1
  AND book_type IS NULL;

-- Regenerate virtual column for alternative book format files, create the index without UNIQUE constraint
ALTER TABLE book_file ADD COLUMN alt_format_current_hash VARCHAR(128) AS (CASE WHEN is_book = true THEN current_hash END) STORED;
ALTER TABLE book_file ADD INDEX idx_book_file_current_hash_alt_format (alt_format_current_hash);

-- Remove constraint from book table
ALTER TABLE book DROP INDEX IF EXISTS unique_library_file_path;

-- Remove migrated fields from the book table
ALTER TABLE book DROP COLUMN file_name;
ALTER TABLE book DROP COLUMN file_sub_path;
ALTER TABLE book DROP COLUMN book_type;
ALTER TABLE book DROP COLUMN file_size_kb;
ALTER TABLE book DROP COLUMN initial_hash;
ALTER TABLE book DROP COLUMN current_hash;
ALTER TABLE book DROP COLUMN archive_type;

DROP TABLE book_file_old;