-- Add per-field lock columns to comic_metadata (previously these shared grouped locks)

-- Fields previously grouped under issue_number_locked
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS imprint_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS format_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS black_and_white_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS manga_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS reading_direction_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS web_link_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS notes_locked BOOLEAN DEFAULT FALSE;

-- Fields previously grouped under story_arc_locked
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS story_arc_number_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS alternate_series_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS alternate_issue_locked BOOLEAN DEFAULT FALSE;

-- Fields previously grouped under creators_locked (per-role locks)
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS pencillers_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS inkers_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS colorists_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS letterers_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS cover_artists_locked BOOLEAN DEFAULT FALSE;
ALTER TABLE comic_metadata ADD COLUMN IF NOT EXISTS editors_locked BOOLEAN DEFAULT FALSE;

-- Initialize new columns from their previous group lock values for existing rows
UPDATE comic_metadata SET imprint_locked = COALESCE(issue_number_locked, FALSE) WHERE imprint_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET format_locked = COALESCE(issue_number_locked, FALSE) WHERE format_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET black_and_white_locked = COALESCE(issue_number_locked, FALSE) WHERE black_and_white_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET manga_locked = COALESCE(issue_number_locked, FALSE) WHERE manga_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET reading_direction_locked = COALESCE(issue_number_locked, FALSE) WHERE reading_direction_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET web_link_locked = COALESCE(issue_number_locked, FALSE) WHERE web_link_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;
UPDATE comic_metadata SET notes_locked = COALESCE(issue_number_locked, FALSE) WHERE notes_locked = FALSE AND COALESCE(issue_number_locked, FALSE) = TRUE;

UPDATE comic_metadata SET story_arc_number_locked = COALESCE(story_arc_locked, FALSE) WHERE story_arc_number_locked = FALSE AND COALESCE(story_arc_locked, FALSE) = TRUE;
UPDATE comic_metadata SET alternate_series_locked = COALESCE(story_arc_locked, FALSE) WHERE alternate_series_locked = FALSE AND COALESCE(story_arc_locked, FALSE) = TRUE;
UPDATE comic_metadata SET alternate_issue_locked = COALESCE(story_arc_locked, FALSE) WHERE alternate_issue_locked = FALSE AND COALESCE(story_arc_locked, FALSE) = TRUE;

UPDATE comic_metadata SET pencillers_locked = COALESCE(creators_locked, FALSE) WHERE pencillers_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
UPDATE comic_metadata SET inkers_locked = COALESCE(creators_locked, FALSE) WHERE inkers_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
UPDATE comic_metadata SET colorists_locked = COALESCE(creators_locked, FALSE) WHERE colorists_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
UPDATE comic_metadata SET letterers_locked = COALESCE(creators_locked, FALSE) WHERE letterers_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
UPDATE comic_metadata SET cover_artists_locked = COALESCE(creators_locked, FALSE) WHERE cover_artists_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
UPDATE comic_metadata SET editors_locked = COALESCE(creators_locked, FALSE) WHERE editors_locked = FALSE AND COALESCE(creators_locked, FALSE) = TRUE;
