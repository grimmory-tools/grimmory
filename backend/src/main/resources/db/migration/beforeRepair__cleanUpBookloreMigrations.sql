-- Booklore 2.3.1 introduces a migration that doesn't actually
-- change the schema but does break Flyway compatibility.
DELETE FROM flyway_schema_history
WHERE version = '133'
  AND script = 'V133__Add_open_library_provider_setting.sql'
  AND checksum = -1017223737;