CREATE TABLE IF NOT EXISTS grimmlink_metadata_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    book_file_id BIGINT,
    item_type VARCHAR(32) NOT NULL,
    dedupe_key VARCHAR(191) NOT NULL,
    device VARCHAR(100),
    device_id VARCHAR(191),
    content_hash VARCHAR(128) NOT NULL,
    payload_json LONGTEXT,
    client_updated_at TIMESTAMP,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_grimmlink_metadata_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_grimmlink_metadata_book FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE,
    CONSTRAINT fk_grimmlink_metadata_book_file FOREIGN KEY (book_file_id) REFERENCES book_file(id) ON DELETE SET NULL,
    CONSTRAINT uk_grimmlink_metadata_user_book_type_dedupe UNIQUE (user_id, book_id, item_type, dedupe_key)
);

CREATE INDEX IF NOT EXISTS idx_grimmlink_metadata_user_book ON grimmlink_metadata_items (user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_grimmlink_metadata_book_file ON grimmlink_metadata_items (book_file_id);
CREATE INDEX IF NOT EXISTS idx_grimmlink_metadata_item_type ON grimmlink_metadata_items (item_type);
CREATE INDEX IF NOT EXISTS idx_grimmlink_metadata_updated_at ON grimmlink_metadata_items (updated_at);

ALTER TABLE reading_sessions
    ADD COLUMN IF NOT EXISTS book_hash VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS device VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS device_id VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS current_page INTEGER NULL,
    ADD COLUMN IF NOT EXISTS total_pages INTEGER NULL;

CREATE INDEX IF NOT EXISTS idx_reading_session_user_book_hash ON reading_sessions (user_id, book_hash, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_reading_session_user_device ON reading_sessions (user_id, device_id, start_time DESC);
