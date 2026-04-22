CREATE TABLE IF NOT EXISTS public_book_review
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    metadata_provider  VARCHAR(255) NOT NULL,
    book_id            BIGINT       NOT NULL,
    reviewer_name      VARCHAR(512),
    title              VARCHAR(512),
    rating             FLOAT,
    date               TIMESTAMP,
    body               TEXT,
    country            VARCHAR(255),
    spoiler            BOOLEAN,
    followers_count    INT,
    text_reviews_count INT,
    PRIMARY KEY (id),
    FOREIGN KEY (book_id) REFERENCES book_metadata (book_id) ON DELETE CASCADE
);