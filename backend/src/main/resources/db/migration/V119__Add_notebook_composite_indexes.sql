CREATE INDEX IF NOT EXISTS idx_annotations_user_created ON annotations (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_book_notes_v2_user_created ON book_notes_v2 (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_book_marks_user_created ON book_marks (user_id, created_at);
