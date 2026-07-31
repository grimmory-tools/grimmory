ALTER TABLE book_file
    ADD COLUMN document_parse_status VARCHAR(16) NULL,
    ALGORITHM = INSTANT,
    LOCK = NONE;
