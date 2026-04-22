ALTER TABLE app_settings
    DROP KEY category;

ALTER TABLE app_settings
    DROP COLUMN name;

ALTER TABLE app_settings
    RENAME COLUMN category TO name;

INSERT INTO app_settings (name, val)
VALUES ('cover_image_resolution', '250x350');