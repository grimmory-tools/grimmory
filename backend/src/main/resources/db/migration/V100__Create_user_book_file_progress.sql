CREATE TABLE user_book_file_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_file_id BIGINT NOT NULL,
    position_data VARCHAR(1000),
    position_href VARCHAR(1000),
    progress_percent FLOAT,
    last_read_time TIMESTAMP,
    CONSTRAINT uk_user_book_file UNIQUE (user_id, book_file_id),
    CONSTRAINT fk_ubfp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubfp_book_file FOREIGN KEY (book_file_id) REFERENCES book_file(id) ON DELETE CASCADE
);

CREATE INDEX idx_ubfp_user_book_file ON user_book_file_progress(user_id, book_file_id);
