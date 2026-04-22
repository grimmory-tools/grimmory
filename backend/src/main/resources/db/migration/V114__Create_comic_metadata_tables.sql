-- Create comic_metadata table for storing comic-specific metadata
CREATE TABLE IF NOT EXISTS comic_metadata
(
    book_id              BIGINT PRIMARY KEY,
    issue_number         VARCHAR(50),
    volume_name          VARCHAR(255),
    volume_number        INTEGER,
    story_arc            VARCHAR(255),
    story_arc_number     INTEGER,
    alternate_series     VARCHAR(255),
    alternate_issue      VARCHAR(50),
    imprint              VARCHAR(255),
    format               VARCHAR(50),
    black_and_white      BOOLEAN     DEFAULT FALSE,
    manga                BOOLEAN     DEFAULT FALSE,
    reading_direction    VARCHAR(10) DEFAULT 'ltr',
    web_link             VARCHAR(1000),
    notes                TEXT,
    issue_number_locked  BOOLEAN     DEFAULT FALSE,
    volume_name_locked   BOOLEAN     DEFAULT FALSE,
    volume_number_locked BOOLEAN     DEFAULT FALSE,
    story_arc_locked     BOOLEAN     DEFAULT FALSE,
    creators_locked      BOOLEAN     DEFAULT FALSE,
    characters_locked    BOOLEAN     DEFAULT FALSE,
    teams_locked         BOOLEAN     DEFAULT FALSE,
    locations_locked     BOOLEAN     DEFAULT FALSE,
    CONSTRAINT fk_comic_metadata_book FOREIGN KEY (book_id) REFERENCES book_metadata (book_id) ON DELETE CASCADE
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_comic_metadata_story_arc ON comic_metadata (story_arc);
CREATE INDEX IF NOT EXISTS idx_comic_metadata_volume_name ON comic_metadata (volume_name);

-- Create comic_character table
CREATE TABLE IF NOT EXISTS comic_character
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Create comic_team table
CREATE TABLE IF NOT EXISTS comic_team
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Create comic_location table
CREATE TABLE IF NOT EXISTS comic_location
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Create comic_creator table
CREATE TABLE IF NOT EXISTS comic_creator
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Create mapping tables
CREATE TABLE IF NOT EXISTS comic_metadata_character_mapping
(
    book_id      BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, character_id),
    CONSTRAINT fk_comic_char_mapping_book FOREIGN KEY (book_id) REFERENCES comic_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_comic_char_mapping_char FOREIGN KEY (character_id) REFERENCES comic_character (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comic_metadata_team_mapping
(
    book_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, team_id),
    CONSTRAINT fk_comic_team_mapping_book FOREIGN KEY (book_id) REFERENCES comic_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_comic_team_mapping_team FOREIGN KEY (team_id) REFERENCES comic_team (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comic_metadata_location_mapping
(
    book_id     BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    PRIMARY KEY (book_id, location_id),
    CONSTRAINT fk_comic_loc_mapping_book FOREIGN KEY (book_id) REFERENCES comic_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_comic_loc_mapping_loc FOREIGN KEY (location_id) REFERENCES comic_location (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comic_metadata_creator_mapping
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id    BIGINT      NOT NULL,
    creator_id BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    CONSTRAINT fk_comic_creator_mapping_book FOREIGN KEY (book_id) REFERENCES comic_metadata (book_id) ON DELETE CASCADE,
    CONSTRAINT fk_comic_creator_mapping_creator FOREIGN KEY (creator_id) REFERENCES comic_creator (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_comic_creator_mapping_role ON comic_metadata_creator_mapping (role);
CREATE INDEX IF NOT EXISTS idx_comic_creator_mapping_book ON comic_metadata_creator_mapping (book_id);
