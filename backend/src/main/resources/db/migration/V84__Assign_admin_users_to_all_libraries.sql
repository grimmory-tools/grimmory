-- Assign all admin users to all libraries
INSERT IGNORE INTO user_library_mapping (user_id, library_id)
SELECT u.id, l.id
FROM users u
CROSS JOIN library l
INNER JOIN user_permissions up ON u.id = up.user_id
WHERE up.permission_admin = true;