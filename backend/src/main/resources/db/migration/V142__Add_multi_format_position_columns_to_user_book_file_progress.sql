ALTER TABLE user_book_file_progress
    ADD COLUMN IF NOT EXISTS position_type          VARCHAR(20)   NULL,
    ADD COLUMN IF NOT EXISTS xpointer               VARCHAR(1000) NULL,
    ADD COLUMN IF NOT EXISTS readium_locator_json   LONGTEXT      NULL,
    ADD COLUMN IF NOT EXISTS chapter_progression    FLOAT         NULL,
    ADD COLUMN IF NOT EXISTS text_before            TEXT          NULL,
    ADD COLUMN IF NOT EXISTS text_highlight         TEXT          NULL,
    ADD COLUMN IF NOT EXISTS text_after             TEXT          NULL;
