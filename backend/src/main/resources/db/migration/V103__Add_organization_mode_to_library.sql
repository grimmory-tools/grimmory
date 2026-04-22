-- Add organization_mode column and migrate from legacy scan_mode
-- FOLDER_AS_BOOK -> BOOK_PER_FOLDER (preserves existing behavior)
-- FILE_AS_BOOK/NULL/unknown -> AUTO_DETECT (new default)

ALTER TABLE library ADD COLUMN organization_mode VARCHAR(50);

-- Migrate known values
UPDATE library SET organization_mode = 'BOOK_PER_FOLDER' WHERE scan_mode = 'FOLDER_AS_BOOK';
UPDATE library SET organization_mode = 'AUTO_DETECT' WHERE scan_mode IS NULL OR scan_mode = 'FILE_AS_BOOK';

-- Safety net: set any remaining NULL values (handles unexpected scan_mode values)
UPDATE library SET organization_mode = 'AUTO_DETECT' WHERE organization_mode IS NULL;

-- Set default and make non-null for future inserts
ALTER TABLE library ALTER COLUMN organization_mode SET DEFAULT 'AUTO_DETECT';
ALTER TABLE library MODIFY COLUMN organization_mode VARCHAR(50) NOT NULL DEFAULT 'AUTO_DETECT';

-- Remove legacy column
ALTER TABLE library DROP COLUMN scan_mode;
