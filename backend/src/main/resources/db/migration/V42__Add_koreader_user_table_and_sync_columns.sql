CREATE TABLE IF NOT EXISTS koreader_user
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(100) NOT NULL UNIQUE,
    password         VARCHAR(255) NOT NULL,
    password_md5     VARCHAR(255) NOT NULL,
    sync_enabled     BOOLEAN,
    booklore_user_id BIGINT,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_booklore_user FOREIGN KEY (booklore_user_id) REFERENCES users (id)
);

ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS koreader_progress         VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS koreader_progress_percent FLOAT,
    ADD COLUMN IF NOT EXISTS koreader_device           VARCHAR(100),
    ADD COLUMN IF NOT EXISTS koreader_device_id        VARCHAR(100),
    ADD COLUMN IF NOT EXISTS koreader_last_sync_time   TIMESTAMP;

ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_sync_koreader BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions
SET permission_sync_koreader = TRUE
WHERE permission_admin = TRUE;

