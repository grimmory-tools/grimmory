CREATE TABLE IF NOT EXISTS reading_sessions
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    book_id          BIGINT       NOT NULL,
    book_type        VARCHAR(10)  NOT NULL,
    start_time       DATETIME     NOT NULL,
    end_time         DATETIME     NOT NULL,
    duration_seconds INTEGER      NOT NULL,
    start_progress   FLOAT        NOT NULL,
    end_progress     FLOAT        NOT NULL,
    progress_delta   FLOAT        NOT NULL,
    start_location   VARCHAR(500) NOT NULL,
    end_location     VARCHAR(500) NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reading_session_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_session_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reading_session_user_time ON reading_sessions (user_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_reading_session_book ON reading_sessions (book_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_reading_session_user_book ON reading_sessions (user_id, book_id, start_time DESC);
