CREATE TABLE kobo_span_map
(
    id            BIGINT AUTO_INCREMENT NOT NULL,
    book_file_id  BIGINT                NOT NULL,
    file_hash     VARCHAR(128)          NOT NULL,
    span_map_json LONGTEXT              NOT NULL,
    created_at    datetime              NOT NULL,
    CONSTRAINT pk_kobo_span_map PRIMARY KEY (id),
    CONSTRAINT uk_kobo_span_map_book_file UNIQUE (book_file_id)
);

ALTER TABLE kobo_span_map
    ADD CONSTRAINT fk_kobo_span_map_book_file
        FOREIGN KEY (book_file_id) REFERENCES book_file (id) ON DELETE CASCADE;
