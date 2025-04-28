CREATE TABLE app_migration
(
    migration_key VARCHAR(100) PRIMARY KEY COMMENT 'Unique identifier for the migration',
    executed_at   TIMESTAMP NOT NULL COMMENT 'When the migration was executed',
    description   TEXT COMMENT 'Optional description of what the migration did'
) COMMENT = 'Tracks one-time application-level data migrations';