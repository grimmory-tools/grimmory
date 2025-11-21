-- Add Kobo progress tracking columns to user_book_progress table
ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS kobo_progress_percent  FLOAT,
    ADD COLUMN IF NOT EXISTS kobo_location          VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS kobo_location_type     VARCHAR(50),
    ADD COLUMN IF NOT EXISTS kobo_location_source   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS kobo_last_sync_time    TIMESTAMP;

-- Add configurable progress thresholds to kobo_user_settings table
ALTER TABLE kobo_user_settings
    ADD COLUMN IF NOT EXISTS progress_mark_as_reading_threshold  FLOAT DEFAULT 1,
    ADD COLUMN IF NOT EXISTS progress_mark_as_finished_threshold FLOAT DEFAULT 99;
