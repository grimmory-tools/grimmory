-- Add per-user Hardcover settings to kobo_user_settings table
ALTER TABLE kobo_user_settings ADD COLUMN hardcover_api_key VARCHAR(2048);
ALTER TABLE kobo_user_settings ADD COLUMN hardcover_sync_enabled BOOLEAN DEFAULT FALSE;

-- Fix kobo_location_source column size (Kobo devices can send longer location strings)
ALTER TABLE user_book_progress MODIFY COLUMN kobo_location_source VARCHAR(512);
