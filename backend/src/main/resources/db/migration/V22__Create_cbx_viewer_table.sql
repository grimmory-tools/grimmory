CREATE TABLE IF NOT EXISTS cbx_viewer_preference
(
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id   BIGINT      NOT NULL,
    book_id   BIGINT      NOT NULL,
    spread    VARCHAR(16) NULL,
    view_mode VARCHAR(16) NULL,
    UNIQUE (user_id, book_id)
);