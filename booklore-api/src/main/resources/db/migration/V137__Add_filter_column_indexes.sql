-- Indexes for filterable columns used by AppBookSpecification.
-- All additive; safe to run on existing data without downtime.

CREATE INDEX IF NOT EXISTS idx_author_name ON author (name);

CREATE INDEX IF NOT EXISTS idx_book_metadata_language ON book_metadata (language);
CREATE INDEX IF NOT EXISTS idx_book_metadata_publisher ON book_metadata (publisher);
CREATE INDEX IF NOT EXISTS idx_book_metadata_series_name ON book_metadata (series_name);
CREATE INDEX IF NOT EXISTS idx_book_metadata_narrator ON book_metadata (narrator);

CREATE INDEX IF NOT EXISTS idx_category_name ON category (name);
CREATE INDEX IF NOT EXISTS idx_tag_name ON tag (name);
CREATE INDEX IF NOT EXISTS idx_mood_name ON mood (name);

-- Composite index for personal-rating filter subqueries
CREATE INDEX IF NOT EXISTS idx_user_book_progress_rating ON user_book_progress (user_id, personal_rating, book_id);
