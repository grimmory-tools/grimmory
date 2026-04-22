ALTER TABLE email_provider
    ADD COLUMN from_address VARCHAR(255) NULL AFTER password;
