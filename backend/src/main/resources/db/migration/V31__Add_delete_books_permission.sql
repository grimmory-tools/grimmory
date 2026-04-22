ALTER TABLE user_permissions
    ADD COLUMN permission_delete_book BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions up
SET up.permission_delete_book = TRUE
WHERE up.permission_admin = TRUE;