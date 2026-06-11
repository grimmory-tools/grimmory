CREATE INDEX IF NOT EXISTS idx_reading_sessions_grimmlink_idempotency
    ON reading_sessions (user_id, book_id, book_hash, start_time, end_time, device_id);
