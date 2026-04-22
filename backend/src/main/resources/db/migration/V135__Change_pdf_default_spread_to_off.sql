-- Change default PDF page spread from 'odd' to 'off' (single page view)
UPDATE user_settings
SET setting_value = REPLACE(setting_value, '"pageSpread":"odd"', '"pageSpread":"off"')
WHERE setting_key = 'PDF_READER_SETTING'
  AND setting_value LIKE '%"pageSpread":"odd"%';

-- Update existing per-book PDF viewer preferences
UPDATE pdf_viewer_preference
SET spread = 'off'
WHERE spread = 'odd';
