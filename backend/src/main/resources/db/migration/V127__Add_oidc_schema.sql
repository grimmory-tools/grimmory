-- OIDC session tracking (for backchannel logout)
CREATE TABLE IF NOT EXISTS oidc_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    oidc_subject VARCHAR(255) NOT NULL,
    oidc_issuer VARCHAR(512) NOT NULL,
    oidc_session_id VARCHAR(255),
    id_token_hint TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_refreshed_at TIMESTAMP NULL,
    revoked BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_oidc_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_oidc_session_user_id ON oidc_session(user_id);
CREATE INDEX IF NOT EXISTS idx_oidc_session_subject ON oidc_session(oidc_subject);
CREATE INDEX IF NOT EXISTS idx_oidc_session_sid ON oidc_session(oidc_session_id);
CREATE INDEX IF NOT EXISTS idx_oidc_session_sub_iss_revoked ON oidc_session(oidc_subject, oidc_issuer, revoked);

-- OIDC fields on users
ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_subject VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS oidc_issuer VARCHAR(512);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1024);
-- NULLs are treated as distinct by MariaDB, so local users (NULL/NULL) don't conflict
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_oidc_issuer_subject ON users(oidc_issuer, oidc_subject);
