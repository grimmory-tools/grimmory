CREATE TABLE book_notes
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    book_id    BIGINT    NOT NULL,
    title      VARCHAR(255),
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_book_notes_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_notes_book_id FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX idx_book_notes_user_id ON book_notes (user_id);
CREATE INDEX idx_book_notes_book_id ON book_notes (book_id);
