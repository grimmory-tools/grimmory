-- Migrate default_book_format (single value) to format_priority (JSON array)
-- This allows users to set a prioritized list of preferred formats with cascading fallback

-- Add new column
ALTER TABLE library ADD COLUMN IF NOT EXISTS format_priority TEXT NULL;

-- Migrate existing data: convert single format to JSON array with that format as first element
UPDATE library
SET format_priority = CONCAT('["', default_book_format, '"]')
WHERE default_book_format IS NOT NULL AND default_book_format != '';

-- Drop old column
ALTER TABLE library DROP COLUMN IF EXISTS default_book_format;
