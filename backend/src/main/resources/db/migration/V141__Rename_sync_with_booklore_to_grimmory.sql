ALTER TABLE koreader_user
    CHANGE COLUMN sync_with_booklore_reader sync_with_grimmory_reader TINYINT(1) NOT NULL DEFAULT 0;
