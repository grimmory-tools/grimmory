CREATE TABLE book_notes_v2
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    user_id       BIGINT                NOT NULL,
    book_id       BIGINT                NOT NULL,
    cfi           VARCHAR(1000)         NOT NULL,
    selected_text VARCHAR(5000)         NULL,
    note_content  TEXT                  NOT NULL,
    color         VARCHAR(20)           NULL,
    chapter_title VARCHAR(500)          NULL,
    version       BIGINT                NOT NULL DEFAULT 0,
    created_at    datetime              NOT NULL,
    updated_at    datetime              NOT NULL,
    CONSTRAINT pk_book_notes_v2 PRIMARY KEY (id),
    CONSTRAINT uq_book_notes_v2_user_book_cfi UNIQUE (user_id, book_id, cfi)
);

CREATE INDEX idx_book_notes_v2_user_id ON book_notes_v2 (user_id);
CREATE INDEX idx_book_notes_v2_book_id ON book_notes_v2 (book_id);
CREATE INDEX idx_book_notes_v2_user_book ON book_notes_v2 (user_id, book_id);

ALTER TABLE book_notes_v2
    ADD CONSTRAINT fk_book_notes_v2_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE book_notes_v2
    ADD CONSTRAINT fk_book_notes_v2_book_id FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
