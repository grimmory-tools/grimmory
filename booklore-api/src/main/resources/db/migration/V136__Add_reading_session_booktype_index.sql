-- Covering index for audiobook-filtered queries (listening stats)
CREATE INDEX IF NOT EXISTS idx_reading_session_user_booktype
    ON reading_sessions (user_id, book_type, start_time DESC);
