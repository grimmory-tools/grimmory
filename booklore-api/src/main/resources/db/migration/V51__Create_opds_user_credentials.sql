CREATE TABLE IF NOT EXISTS opds_user_v2
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_opds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_userid_username UNIQUE (user_id, username)
);

ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_access_opds BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions
SET permission_access_opds = TRUE
WHERE permission_admin = TRUE;