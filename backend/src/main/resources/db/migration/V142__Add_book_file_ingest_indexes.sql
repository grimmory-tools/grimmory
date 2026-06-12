CREATE INDEX IF NOT EXISTS idx_book_file_current_hash ON book_file (current_hash);
CREATE INDEX IF NOT EXISTS idx_book_file_path ON book_file (file_sub_path(255), file_name(255));
