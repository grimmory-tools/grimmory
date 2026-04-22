CREATE TABLE IF NOT EXISTS kobo_user_settings
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL UNIQUE,
    token        VARCHAR(2048) NOT NULL,
    sync_enabled BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kobo_library_snapshot
(
    id           VARCHAR(36) PRIMARY KEY,
    user_id      BIGINT    NOT NULL,
    created_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_snapshot_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kobo_library_snapshot_book
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id VARCHAR(36) NOT NULL,
    book_id     BIGINT      NOT NULL,
    synced      BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_snapshot_book FOREIGN KEY (snapshot_id) REFERENCES kobo_library_snapshot (id) ON DELETE CASCADE,
    CONSTRAINT uq_snapshot_book UNIQUE (snapshot_id, book_id)
);

CREATE TABLE IF NOT EXISTS kobo_reading_state
(
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    entitlement_id        VARCHAR(255) NOT NULL UNIQUE,
    created               VARCHAR(255) NULL,
    last_modified         VARCHAR(255) NULL,
    priority_timestamp    VARCHAR(255) NULL,
    current_bookmark_json JSON,
    statistics_json       JSON,
    status_info_json      JSON,
    last_modified_string  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS kobo_removed_books_tracking
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id    VARCHAR(36) NOT NULL,
    user_id        BIGINT      NOT NULL,
    book_id_synced BIGINT      NOT NULL,
    CONSTRAINT uq_snapshot_user_book UNIQUE (snapshot_id, user_id, book_id_synced),
    CONSTRAINT fk_removed_snapshot FOREIGN KEY (snapshot_id) REFERENCES kobo_library_snapshot (id) ON DELETE CASCADE
);

ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_sync_kobo BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions
SET permission_sync_kobo = TRUE
WHERE permission_admin = TRUE;