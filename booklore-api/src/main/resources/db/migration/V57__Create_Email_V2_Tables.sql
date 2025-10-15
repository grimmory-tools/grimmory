CREATE TABLE IF NOT EXISTS email_provider_v2
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    host         VARCHAR(255) NOT NULL,
    port         INT          NOT NULL,
    username     VARCHAR(255) NOT NULL,
    password     VARCHAR(255) NOT NULL,
    from_address VARCHAR(255),
    auth         BOOLEAN      NOT NULL,
    start_tls    BOOLEAN      NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_provider_v2_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE (user_id, name)
);

CREATE TABLE IF NOT EXISTS email_recipient_v2
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    is_default BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_recipient_v2_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    UNIQUE (user_id, email)
);

CREATE INDEX idx_email_provider_v2_user_id ON email_provider_v2 (user_id);
CREATE INDEX idx_email_recipient_v2_user_id ON email_recipient_v2 (user_id);

