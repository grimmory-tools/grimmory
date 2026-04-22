CREATE TABLE metadata_fetch_jobs
(
    task_id           VARCHAR(100) PRIMARY KEY,
    user_id           BIGINT,
    status            VARCHAR(20) NOT NULL DEFAULT 'pending',
    status_message    TEXT,
    started_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP            DEFAULT NULL,
    total_books_count INT,
    completed_books   INT                  DEFAULT 0
);

CREATE TABLE metadata_fetch_proposals
(
    proposal_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id          VARCHAR(100) NOT NULL,
    book_id          BIGINT       NOT NULL,
    fetched_at       TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    reviewed_at      TIMESTAMP             DEFAULT NULL,
    reviewer_user_id BIGINT,
    status           VARCHAR(30)  NOT NULL DEFAULT 'pending',
    metadata_json    JSON         NOT NULL,

    CONSTRAINT fk_metadata_fetch_task
        FOREIGN KEY (task_id)
            REFERENCES metadata_fetch_jobs (task_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_metadata_proposal_task_id ON metadata_fetch_proposals (task_id);
CREATE INDEX idx_metadata_proposal_book_id ON metadata_fetch_proposals (book_id);
CREATE INDEX idx_metadata_proposal_status ON metadata_fetch_proposals (status);