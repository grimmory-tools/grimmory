CREATE TABLE IF NOT EXISTS task_cron_configuration
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type       VARCHAR(100) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      BIGINT       NOT NULL DEFAULT -1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_type UNIQUE (task_type)
);

INSERT INTO task_cron_configuration (task_type, cron_expression, enabled, created_by)
VALUES
       ('CLEAR_CBX_CACHE',             '0 30 0 * * 1', FALSE, -1), -- Run CLEAR_CBX_CACHE every Monday at 12:30 AM
       ('CLEAR_PDF_CACHE',             '0 35 0 * * 1', FALSE, -1), -- Run CLEAR_PDF_CACHE every Monday at 12:35 AM
       ('CLEANUP_DELETED_BOOKS',       '0 40 0 * * 1', TRUE,  -1), -- Run CLEANUP_DELETED_BOOKS every Monday at 12:40 AM
       ('CLEANUP_TEMP_METADATA',       '0 45 0 * * 1', TRUE,  -1), -- Run CLEANUP_TEMP_METADATA every Monday at 12:45 AM
       ('SYNC_LIBRARY_FILES',          '0 0 1 * * *',  TRUE,  -1), -- Run RE_SCAN_LIBRARY every day at 1:00 AM
       ('UPDATE_BOOK_RECOMMENDATIONS', '0 30 1 * * *', TRUE,  -1); -- Run UPDATE_BOOK_RECOMMENDATIONS every day at 1:30 AM
