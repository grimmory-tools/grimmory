ALTER TABLE book_metadata
    ADD COLUMN personal_rating         FLOAT,
    ADD COLUMN personal_rating_locked BOOLEAN DEFAULT FALSE