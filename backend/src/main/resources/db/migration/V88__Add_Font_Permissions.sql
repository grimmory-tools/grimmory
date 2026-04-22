ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_manage_fonts BOOLEAN NOT NULL DEFAULT FALSE;

-- Set all new permissions to TRUE for admin users

UPDATE user_permissions up
SET up.permission_manage_fonts = TRUE
WHERE up.permission_admin = TRUE;
