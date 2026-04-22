ALTER TABLE book_marks ADD COLUMN page_number INT NULL;
CREATE UNIQUE INDEX uq_book_marks_user_book_page_number
    ON book_marks (user_id, book_id, page_number);
