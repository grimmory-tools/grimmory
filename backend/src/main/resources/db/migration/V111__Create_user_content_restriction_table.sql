-- Create user_content_restriction table for per-user content filtering
CREATE TABLE user_content_restriction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    restriction_type VARCHAR(20) NOT NULL,
    mode VARCHAR(15) NOT NULL,
    value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ucr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_restriction UNIQUE (user_id, restriction_type, value)
);

CREATE INDEX idx_ucr_user_id ON user_content_restriction(user_id);
