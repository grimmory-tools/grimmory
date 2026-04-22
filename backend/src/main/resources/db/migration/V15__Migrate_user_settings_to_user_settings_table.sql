START TRANSACTION;

INSERT IGNORE INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'perBookSetting' AS setting_key,
    JSON_EXTRACT(book_preferences, '$.perBookSetting') AS setting_value
FROM users
WHERE JSON_EXTRACT(book_preferences, '$.perBookSetting') IS NOT NULL
  AND JSON_EXTRACT(book_preferences, '$.perBookSetting') != '{}'
  AND JSON_EXTRACT(book_preferences, '$.perBookSetting') != '""';

INSERT IGNORE INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'pdfReaderSetting' AS setting_key,
    JSON_EXTRACT(book_preferences, '$.pdfReaderSetting') AS setting_value
FROM users
WHERE JSON_EXTRACT(book_preferences, '$.pdfReaderSetting') IS NOT NULL
  AND JSON_EXTRACT(book_preferences, '$.pdfReaderSetting') != '{}'
  AND JSON_EXTRACT(book_preferences, '$.pdfReaderSetting') != '""';

INSERT IGNORE INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'epubReaderSetting' AS setting_key,
    JSON_EXTRACT(book_preferences, '$.epubReaderSetting') AS setting_value
FROM users
WHERE JSON_EXTRACT(book_preferences, '$.epubReaderSetting') IS NOT NULL
  AND JSON_EXTRACT(book_preferences, '$.epubReaderSetting') != '{}'
  AND JSON_EXTRACT(book_preferences, '$.epubReaderSetting') != '""';

COMMIT;