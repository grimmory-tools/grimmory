CREATE TABLE IF NOT EXISTS nas_synthetic_job
(
    operation_id CHAR(36) NOT NULL PRIMARY KEY,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_nas_synthetic_job_status_created ON nas_synthetic_job (status, created_at);

CREATE TABLE IF NOT EXISTS nas_synthetic_replacement_decision
(
    operation_id CHAR(36) NOT NULL PRIMARY KEY,
    request_json LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    finalized_at TIMESTAMP NULL
);

CREATE INDEX IF NOT EXISTS idx_nas_synthetic_replacement_finalize
    ON nas_synthetic_replacement_decision (status, finalized_at, operation_id);

CREATE TABLE IF NOT EXISTS nas_synthetic_write_gate
(
    gate_id INTEGER NOT NULL PRIMARY KEY,
    operation_id CHAR(36) NULL
);

INSERT INTO nas_synthetic_write_gate (gate_id, operation_id)
SELECT 1, NULL WHERE NOT EXISTS (SELECT 1 FROM nas_synthetic_write_gate WHERE gate_id = 1);
