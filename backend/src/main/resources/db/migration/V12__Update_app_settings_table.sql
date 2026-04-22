ALTER TABLE app_settings
    MODIFY COLUMN name VARCHAR(255) NOT NULL,
    ADD UNIQUE INDEX uq_app_settings_name (name);

ALTER TABLE app_settings
    MODIFY COLUMN val TEXT NULL;