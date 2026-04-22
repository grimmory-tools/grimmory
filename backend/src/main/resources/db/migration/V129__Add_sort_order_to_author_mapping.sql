ALTER TABLE book_metadata_author_mapping
  ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- Assign unique sort_order per book for existing rows
SET @prev_book := 0;
SET @pos := 0;

UPDATE book_metadata_author_mapping
SET sort_order = (@pos := IF(@prev_book = book_id, @pos + 1, 0)),
    book_id = (@prev_book := book_id)
ORDER BY book_id, author_id;

-- Change PK from (book_id, author_id) to (book_id, sort_order)
ALTER TABLE book_metadata_author_mapping
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (book_id, sort_order);

-- Keep an index on author_id for reverse lookups
CREATE INDEX IF NOT EXISTS idx_author_mapping_author_id ON book_metadata_author_mapping (book_id, author_id);
