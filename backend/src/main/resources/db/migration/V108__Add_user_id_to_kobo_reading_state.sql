ALTER TABLE kobo_reading_state
    ADD COLUMN IF NOT EXISTS user_id BIGINT NULL;

ALTER TABLE kobo_reading_state
    ADD CONSTRAINT fk_kobo_reading_state_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE kobo_reading_state
    DROP INDEX IF EXISTS entitlement_id;

ALTER TABLE kobo_reading_state
    ADD UNIQUE INDEX uq_kobo_reading_state_user_entitlement (user_id, entitlement_id);

CREATE INDEX IF NOT EXISTS idx_kobo_reading_state_entitlement_id
    ON kobo_reading_state (entitlement_id);
