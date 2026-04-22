-- Make reading session columns nullable for audiobook support
-- Audiobooks may not have progress percentage data

ALTER TABLE reading_sessions
    ADD COLUMN duration_formatted VARCHAR(50) NULL;

ALTER TABLE reading_sessions
    MODIFY COLUMN start_progress FLOAT NULL;

ALTER TABLE reading_sessions
    MODIFY COLUMN end_progress FLOAT NULL;

ALTER TABLE reading_sessions
    MODIFY COLUMN progress_delta FLOAT NULL;

ALTER TABLE reading_sessions
    MODIFY COLUMN start_location VARCHAR(500) NULL;

ALTER TABLE reading_sessions
    MODIFY COLUMN end_location VARCHAR(500) NULL;
