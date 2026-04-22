ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS kobo_status_sent_time TIMESTAMP;

ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS read_status_modified_time TIMESTAMP;

ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS kobo_progress_sent_time TIMESTAMP;

ALTER TABLE user_book_progress
    CHANGE COLUMN kobo_last_sync_time kobo_progress_received_time TIMESTAMP;
