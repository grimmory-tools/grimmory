ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_convert_book BOOLEAN NOT NULL DEFAULT FALSE;

-- Set the new permission to TRUE for admin users
UPDATE user_permissions up
SET up.permission_convert_book = TRUE
WHERE up.permission_admin = TRUE;
