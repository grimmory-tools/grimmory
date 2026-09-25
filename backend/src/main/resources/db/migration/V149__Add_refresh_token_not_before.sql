-- Add a not_before field to refresh_token similar to nbf claim
ALTER TABLE refresh_token
    ADD COLUMN IF NOT EXISTS not_before_date DATETIME NOT NULL DEFAULT NOW();
