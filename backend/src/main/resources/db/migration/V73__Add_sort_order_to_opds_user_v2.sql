-- Add sort_order column to opds_user_v2 table
ALTER TABLE opds_user_v2 ADD COLUMN sort_order VARCHAR(20) NOT NULL DEFAULT 'RECENT';
