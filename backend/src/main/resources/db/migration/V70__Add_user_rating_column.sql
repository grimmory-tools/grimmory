ALTER TABLE user_book_progress ADD COLUMN IF NOT EXISTS personal_rating TINYINT;

-- Copies existing personal ratings to all users with progress records for matching books
UPDATE user_book_progress ubp
JOIN book_metadata bm ON ubp.book_id = bm.book_id
SET ubp.personal_rating = bm.personal_rating
WHERE bm.personal_rating IS NOT NULL;

-- Drops obsolete columns
ALTER TABLE book_metadata DROP COLUMN IF EXISTS personal_rating;
ALTER TABLE book_metadata DROP COLUMN IF EXISTS personal_rating_locked;