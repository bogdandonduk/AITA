-- Adds per-store/branch worker shift passwords and workshift ledger.
ALTER TABLE store_worker_memberships
    ADD COLUMN IF NOT EXISTS workshift_password_hash TEXT;

ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS workshift_password_hash TEXT;

CREATE TABLE IF NOT EXISTS workshifts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    worker_membership_id UUID NOT NULL REFERENCES store_worker_memberships(id) ON DELETE CASCADE,
    worker_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at_millis BIGINT NOT NULL,
    ended_at_millis BIGINT,
    started_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ended_by_user_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS workshifts_store_worker_active_idx
    ON workshifts(store_id, worker_user_id, is_active, started_at_millis DESC);

CREATE INDEX IF NOT EXISTS workshifts_membership_idx
    ON workshifts(worker_membership_id);

CREATE UNIQUE INDEX IF NOT EXISTS workshifts_one_active_per_store_worker_idx
    ON workshifts(store_id, worker_user_id)
    WHERE is_active = TRUE AND ended_at_millis IS NULL;
