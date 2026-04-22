-- Migrate MetadataPersistenceSettings from old structure to new nested structure
-- Old: {"saveToOriginalFile":boolean,...}
-- New: {"saveToOriginalFile":{"epub":{...},"pdf":{...}},...}
-- Creates metadata_persistence_settings_v2 instead of overwriting the original

INSERT INTO app_settings (name, val)
SELECT
    'metadata_persistence_settings_v2',
    CASE
        -- If saveToOriginalFile is true, enable all formats
        WHEN JSON_EXTRACT(val, '$.saveToOriginalFile') = true THEN
            JSON_SET(
                val,
                '$.saveToOriginalFile',
                JSON_OBJECT(
                    'epub', JSON_OBJECT('enabled', true, 'maxFileSizeInMb', 250),
                    'pdf', JSON_OBJECT('enabled', true, 'maxFileSizeInMb', 250),
                    'cbx', JSON_OBJECT('enabled', true, 'maxFileSizeInMb', 250)
                )
            )
        -- If saveToOriginalFile is false, disable all formats
        WHEN JSON_EXTRACT(val, '$.saveToOriginalFile') = false THEN
            JSON_SET(
                val,
                '$.saveToOriginalFile',
                JSON_OBJECT(
                    'epub', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250),
                    'pdf', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250),
                    'cbx', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250)
                )
            )
        -- If null or missing, default to disabled
        ELSE
            JSON_SET(
                COALESCE(val, '{}'),
                '$.saveToOriginalFile',
                JSON_OBJECT(
                    'epub', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250),
                    'pdf', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250),
                    'cbx', JSON_OBJECT('enabled', false, 'maxFileSizeInMb', 250)
                )
            )
    END
FROM app_settings
WHERE name = 'metadata_persistence_settings'
AND val IS NOT NULL
AND val != ''
AND NOT EXISTS (
    SELECT 1 FROM app_settings WHERE name = 'metadata_persistence_settings_v2'
);
