CREATE TABLE IF NOT EXISTS mood
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS tag
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

ALTER TABLE book_metadata
ADD COLUMN moods_locked BOOLEAN DEFAULT FALSE,
ADD COLUMN tags_locked BOOLEAN DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS book_metadata_mood_mapping
(
    book_id     BIGINT NOT NULL,
    mood_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, mood_id),
    CONSTRAINT fk_book_metadata_mood_mapping_book FOREIGN KEY (book_id) REFERENCES book_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_book_metadata_mood_mapping_mood FOREIGN KEY (mood_id) REFERENCES mood (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS book_metadata_tag_mapping
(
    book_id     BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, tag_id),
    CONSTRAINT fk_book_metadata_tag_mapping_book FOREIGN KEY (book_id) REFERENCES book_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_book_metadata_tag_mapping_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
);