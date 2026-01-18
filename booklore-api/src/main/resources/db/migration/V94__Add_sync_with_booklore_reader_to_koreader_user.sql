ALTER TABLE koreader_user
    ADD COLUMN IF NOT EXISTS sync_with_booklore_reader BOOLEAN NOT NULL DEFAULT FALSE;
