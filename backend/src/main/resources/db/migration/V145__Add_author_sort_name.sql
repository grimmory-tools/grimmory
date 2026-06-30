ALTER TABLE author ADD COLUMN IF NOT EXISTS sort_name VARCHAR(255);
ALTER TABLE author ADD COLUMN IF NOT EXISTS sort_name_locked BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE author SET sort_name = name WHERE sort_name IS NULL;
CREATE INDEX IF NOT EXISTS idx_author_sort_name ON author (sort_name);
