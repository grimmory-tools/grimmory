ALTER TABLE book_metadata
    ADD COLUMN amazon_rating                 FLOAT,
    ADD COLUMN amazon_review_count           INT,
    ADD COLUMN goodreads_rating              FLOAT,
    ADD COLUMN goodreads_review_count        INT,
    ADD COLUMN amazon_rating_locked          BOOLEAN DEFAULT FALSE,
    ADD COLUMN amazon_review_count_locked    BOOLEAN DEFAULT FALSE,
    ADD COLUMN goodreads_rating_locked       BOOLEAN DEFAULT FALSE,
    ADD COLUMN goodreads_review_count_locked BOOLEAN DEFAULT FALSE;