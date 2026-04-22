ALTER TABLE refresh_token
    MODIFY COLUMN expiry_date DATETIME(6) NOT NULL,
    MODIFY COLUMN revocation_date DATETIME(6) NULL;
