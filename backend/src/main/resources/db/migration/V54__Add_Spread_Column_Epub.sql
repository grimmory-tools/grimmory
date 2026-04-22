ALTER TABLE epub_viewer_preference
    ADD COLUMN IF NOT EXISTS spread VARCHAR(20) DEFAULT 'double';