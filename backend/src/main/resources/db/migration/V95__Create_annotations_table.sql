CREATE TABLE annotations
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    user_id       BIGINT                NOT NULL,
    book_id       BIGINT                NOT NULL,
    cfi           VARCHAR(1000)         NOT NULL,
    text          VARCHAR(5000)         NOT NULL,
    color         VARCHAR(20)           NULL,
    style         VARCHAR(50)           NULL,
    note          VARCHAR(5000)         NULL,
    chapter_title VARCHAR(500)          NULL,
    version       BIGINT                NOT NULL DEFAULT 0,
    created_at    datetime              NOT NULL,
    updated_at    datetime              NOT NULL,
    CONSTRAINT pk_annotations PRIMARY KEY (id),
    CONSTRAINT uq_annotation_user_book_cfi UNIQUE (user_id, book_id, cfi)
);

CREATE INDEX idx_annotations_user_id ON annotations (user_id);
CREATE INDEX idx_annotations_book_id ON annotations (book_id);
CREATE INDEX idx_annotations_user_book ON annotations (user_id, book_id);

ALTER TABLE annotations
    ADD CONSTRAINT fk_annotations_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE annotations
    ADD CONSTRAINT fk_annotations_book_id FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
