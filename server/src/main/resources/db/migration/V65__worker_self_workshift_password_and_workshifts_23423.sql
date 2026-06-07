ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS workshift_password_hash TEXT;

ALTER TABLE store_worker_memberships
    ADD COLUMN IF NOT EXISTS workshift_password_hash TEXT;

CREATE TABLE IF NOT EXISTS workshifts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    worker_membership_id UUID NOT NULL REFERENCES store_worker_memberships(id) ON DELETE CASCADE,
    worker_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    started_at_millis BIGINT NOT NULL,
    ended_at_millis BIGINT NULL,
    started_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    ended_by_user_id UUID NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE operation_logs
    ADD COLUMN IF NOT EXISTS workshift_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_operation_logs_workshift_id_workshifts_id'
    ) THEN
        ALTER TABLE operation_logs
            ADD CONSTRAINT fk_operation_logs_workshift_id_workshifts_id
            FOREIGN KEY (workshift_id) REFERENCES workshifts(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_workshifts_store_worker_active
    ON workshifts(store_id, worker_user_id, is_active, ended_at_millis);

CREATE INDEX IF NOT EXISTS idx_workshifts_membership
    ON workshifts(worker_membership_id);

CREATE INDEX IF NOT EXISTS idx_workshifts_started_at_millis
    ON workshifts(started_at_millis DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_workshifts_one_open_shift_per_worker_store
    ON workshifts(store_id, worker_user_id)
    WHERE is_active = TRUE AND ended_at_millis IS NULL;

CREATE INDEX IF NOT EXISTS idx_store_worker_memberships_user_store_active
    ON store_worker_memberships(user_id, store_id, is_active);

CREATE INDEX IF NOT EXISTS idx_store_worker_memberships_password_present
    ON store_worker_memberships(id)
    WHERE workshift_password_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_operation_logs_workshift_id
    ON operation_logs(workshift_id);
