CREATE TABLE IF NOT EXISTS audit_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NULL,
    username    VARCHAR(255)  NOT NULL,
    action      VARCHAR(100)  NOT NULL,
    entity_type VARCHAR(100)  NULL,
    entity_id   BIGINT        NULL,
    description VARCHAR(1024) NOT NULL,
    ip_address  VARCHAR(45)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log (action);
