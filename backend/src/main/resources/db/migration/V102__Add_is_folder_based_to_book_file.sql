-- Add is_folder_based column to book_file table for folder-based audiobooks
ALTER TABLE book_file ADD COLUMN is_folder_based BOOLEAN NOT NULL DEFAULT FALSE;
