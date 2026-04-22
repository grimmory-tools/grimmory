CREATE TABLE tasks
(
    id                  VARCHAR(36) NOT NULL PRIMARY KEY,
    type                VARCHAR(50) NOT NULL,
    status              VARCHAR(50) NOT NULL,
    user_id             BIGINT      NOT NULL,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME,
    completed_at        DATETIME,
    progress_percentage INT,
    message             TEXT,
    errorDetails        TEXT,
    task_options        TEXT,
    INDEX idx_tasks_user_id (user_id),
    INDEX idx_tasks_type (type),
    INDEX idx_tasks_status (status),
    INDEX idx_tasks_created_at (created_at)
);

