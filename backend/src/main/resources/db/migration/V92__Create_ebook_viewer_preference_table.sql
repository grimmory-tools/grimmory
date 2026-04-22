CREATE TABLE IF NOT EXISTS ebook_viewer_preference
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    book_id          BIGINT       NOT NULL,
    font_family      VARCHAR(128) NULL,
    font_size        INT          NULL,
    gap              FLOAT        NULL,
    hyphenate        BOOLEAN      NULL,
    is_dark          BOOLEAN      NULL,
    justify          BOOLEAN      NULL,
    line_height      FLOAT        NULL,
    max_block_size   INT          NULL,
    max_column_count INT          NULL,
    max_inline_size  INT          NULL,
    theme            VARCHAR(64)  NULL,
    flow             VARCHAR(32)  NULL,
    UNIQUE (user_id, book_id),
    CONSTRAINT fk_ebook_viewer_preference_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ebook_viewer_preference_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS epub_progress_href VARCHAR(1000);