CREATE TABLE IF NOT EXISTS pdf_annotations
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    book_id    BIGINT   NOT NULL,
    data       LONGTEXT NOT NULL,
    version    BIGINT   NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE (user_id, book_id)
);

CREATE INDEX IF NOT EXISTS idx_pdf_annotations_user_id ON pdf_annotations (user_id);
CREATE INDEX IF NOT EXISTS idx_pdf_annotations_book_id ON pdf_annotations (book_id);

ALTER TABLE pdf_annotations
    ADD CONSTRAINT fk_pdf_annotations_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE pdf_annotations
    ADD CONSTRAINT fk_pdf_annotations_book
        FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
