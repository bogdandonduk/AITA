-- V42__cash_registers_and_store_workers.sql
-- Cash register ledger + flexible store worker/employment permissions.

CREATE TABLE IF NOT EXISTS cash_registers (
    store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE CASCADE,
    current_amount DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    currency_code TEXT NOT NULL DEFAULT 'KZT',
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cash_register_events (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    balance_before DOUBLE PRECISION NOT NULL,
    balance_after DOUBLE PRECISION NOT NULL,
    transaction_id UUID NULL,
    note TEXT NULL,
    time_millis BIGINT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_register_events_store_time
    ON cash_register_events(store_id, time_millis DESC);

CREATE INDEX IF NOT EXISTS idx_cash_register_events_transaction
    ON cash_register_events(transaction_id);

INSERT INTO cash_registers(store_id, current_amount, currency_code, updated_at_millis)
SELECT s.id, 0.0, 'KZT', 0
FROM stores s
ON CONFLICT (store_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS store_worker_requests (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    requester_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending',
    requested_at_millis BIGINT NOT NULL,
    decided_at_millis BIGINT NULL,
    decided_by_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    role_id TEXT NOT NULL DEFAULT 'standard',
    permissions JSONB NOT NULL DEFAULT '["sale_transaction","return_transaction","stock_read","transaction_history_view","cash_register_view"]'::jsonb,
    note TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_store_worker_requests_store_status_time
    ON store_worker_requests(store_id, status, requested_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_store_worker_requests_user_time
    ON store_worker_requests(requester_user_id, requested_at_millis DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_store_worker_requests_one_pending
    ON store_worker_requests(store_id, requester_user_id)
    WHERE status = 'pending';

CREATE TABLE IF NOT EXISTS store_worker_memberships (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_id UUID NULL REFERENCES store_worker_requests(id) ON DELETE SET NULL,
    role_id TEXT NOT NULL DEFAULT 'standard',
    permissions JSONB NOT NULL DEFAULT '["sale_transaction","return_transaction","stock_read","transaction_history_view","cash_register_view"]'::jsonb,
    requested_at_millis BIGINT NOT NULL DEFAULT 0,
    accepted_at_millis BIGINT NOT NULL,
    accepted_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_store_worker_memberships_store_active
    ON store_worker_memberships(store_id, is_active, accepted_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_store_worker_memberships_user_active
    ON store_worker_memberships(user_id, is_active, accepted_at_millis DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_store_worker_memberships_one_active
    ON store_worker_memberships(store_id, user_id)
    WHERE is_active = TRUE;
