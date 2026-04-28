-- V141__Add_sort_name_to_author.sql

-- Add the column using basic ANSI SQL
ALTER TABLE author ADD sort_name VARCHAR(255);

-- Initial backfill using SQL logic: "First Middle Last" -> "Last, First Middle"
-- We use TRIM(name) inline to avoid destructive modification of the source column.
UPDATE author
SET sort_name = CASE
    WHEN INSTR(TRIM(name), ' ') > 0 THEN
        CONCAT(
            SUBSTRING(TRIM(name), LENGTH(TRIM(name)) - INSTR(REVERSE(TRIM(name)), ' ') + 2),
            ', ',
            SUBSTRING(TRIM(name), 1, LENGTH(TRIM(name)) - INSTR(REVERSE(TRIM(name)), ' '))
        )
    ELSE TRIM(name)
END
WHERE sort_name IS NULL AND name IS NOT NULL;

-- Index (standard syntax)
CREATE INDEX idx_author_sort_name ON author(sort_name);
