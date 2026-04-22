CREATE TABLE IF NOT EXISTS email_provider
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    host       VARCHAR(255) NOT NULL,
    port       INT          NOT NULL,
    username   VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    auth       BOOLEAN      NOT NULL,
    start_tls  BOOLEAN      NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, host, username)
);

CREATE TABLE IF NOT EXISTS email_recipient
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE user_permissions
    ADD COLUMN permission_email_book BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE user_permissions up
    JOIN users u ON up.user_id = u.id
SET up.permission_email_book = TRUE
WHERE u.name = 'admin';
