CREATE TABLE IF NOT EXISTS user_email_provider_preference
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    default_provider_id BIGINT NOT NULL,
    CONSTRAINT uq_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_email_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_email_preference_provider FOREIGN KEY (default_provider_id) REFERENCES email_provider_v2 (id) ON DELETE CASCADE
)

