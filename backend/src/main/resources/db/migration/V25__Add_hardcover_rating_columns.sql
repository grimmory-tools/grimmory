ALTER TABLE book_metadata
    ADD COLUMN hardcover_rating                 FLOAT,
    ADD COLUMN hardcover_review_count           INT,
    ADD COLUMN hardcover_rating_locked          BOOLEAN DEFAULT FALSE,
    ADD COLUMN hardcover_review_count_locked    BOOLEAN DEFAULT FALSE