ALTER TABLE library
    ADD COLUMN IF NOT EXISTS file_naming_pattern VARCHAR(1000);

