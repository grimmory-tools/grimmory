-- Usenet indexers
CREATE TABLE acquisition_indexer
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL,
    url        VARCHAR(1000) NOT NULL,
    api_key    VARCHAR(255),
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    priority   INT           NOT NULL DEFAULT 0,
    created_at TIMESTAMP              DEFAULT CURRENT_TIMESTAMP
);

-- Download clients (SABnzbd, etc.)
CREATE TABLE acquisition_client
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(255)  NOT NULL,
    type       VARCHAR(32)   NOT NULL,
    url        VARCHAR(1000) NOT NULL,
    api_key    VARCHAR(255)  NOT NULL,
    category   VARCHAR(255),
    enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP              DEFAULT CURRENT_TIMESTAMP
);

-- Wanted books
CREATE TABLE wanted_book
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(255)  NOT NULL,
    author           VARCHAR(255),
    isbn_13          VARCHAR(13),
    isbn_10          VARCHAR(10),
    provider         VARCHAR(32),
    provider_book_id VARCHAR(255),
    thumbnail_url    VARCHAR(1000),
    status           VARCHAR(32)   NOT NULL DEFAULT 'WANTED',
    last_checked_at  TIMESTAMP     NULL,
    download_id      VARCHAR(255),
    added_by         BIGINT,
    added_at         TIMESTAMP              DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wanted_book_user FOREIGN KEY (added_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Acquisition job history
CREATE TABLE acquisition_job_history
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    wanted_book_id BIGINT        NOT NULL,
    indexer_id     BIGINT,
    nzb_title      VARCHAR(1000),
    nzb_url        VARCHAR(2000),
    confidence     INT,
    status         VARCHAR(32),
    attempted_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_job_history_wanted FOREIGN KEY (wanted_book_id) REFERENCES wanted_book (id) ON DELETE CASCADE
);
