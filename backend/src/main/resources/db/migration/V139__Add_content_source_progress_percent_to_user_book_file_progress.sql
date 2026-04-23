ALTER TABLE user_book_file_progress
    ADD COLUMN IF NOT EXISTS content_source_progress_percent FLOAT NULL;
