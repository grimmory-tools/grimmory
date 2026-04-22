CREATE TABLE IF NOT EXISTS book_additional_file
(
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id              BIGINT       NOT NULL,
    file_name            VARCHAR(1000) NOT NULL,
    file_sub_path        VARCHAR(512) NOT NULL,
    additional_file_type ENUM('ALTERNATIVE_FORMAT', 'SUPPLEMENTARY') NOT NULL,
    file_size_kb         BIGINT,
    initial_hash         VARCHAR(128),
    current_hash         VARCHAR(128),
    description          TEXT,
    added_on             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Virtual column for alternative format files
    alt_format_current_hash     VARCHAR(128) AS (CASE WHEN additional_file_type = 'ALTERNATIVE_FORMAT' THEN current_hash END) STORED,

    CONSTRAINT fk_book_additional_file_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE,
    UNIQUE INDEX idx_book_additional_file_current_hash_alt_format (alt_format_current_hash)
);

CREATE INDEX idx_book_additional_file_book_id ON book_additional_file(book_id);
