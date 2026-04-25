ALTER TABLE author ADD COLUMN has_photo BOOLEAN NOT NULL DEFAULT FALSE;

-- Initialize has_photo based on existing files is hard in SQL, 
-- but we can at least add the column. 
-- A maintenance task could populate this later if needed, 
-- or we can assume it will be populated as authors are updated.
