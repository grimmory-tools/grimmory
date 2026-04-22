CREATE TABLE refresh_token
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT    NOT NULL,
    token           VARCHAR(512) NOT NULL,
    expiry_date     TIMESTAMP NOT NULL,
    revoked         BOOLEAN   NOT NULL DEFAULT FALSE,
    revocation_date TIMESTAMP NULL,

    CONSTRAINT uq_refresh_token UNIQUE (token),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE
);