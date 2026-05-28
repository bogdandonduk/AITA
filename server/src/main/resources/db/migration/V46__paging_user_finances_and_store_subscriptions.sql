-- V46: user finances, AITA national-currency wallet, payment intents, and store subscriptions.
-- Keeps wallet values in minor currency units (cents/tiyn/diram-like 1/100 unit) to avoid floating-point money drift.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS user_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    currency_code TEXT NOT NULL,
    balance_minor BIGINT NOT NULL DEFAULT 0,
    reserved_minor BIGINT NOT NULL DEFAULT 0,
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_wallet_ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_id UUID NOT NULL REFERENCES user_wallets(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    balance_before_minor BIGINT NOT NULL,
    balance_after_minor BIGINT NOT NULL,
    currency_code TEXT NOT NULL,
    reference_type TEXT NOT NULL DEFAULT '',
    reference_id TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    created_at_millis BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS top_up_payment_intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code TEXT NOT NULL,
    status TEXT NOT NULL,
    provider_invoice_id TEXT NOT NULL DEFAULT '',
    payment_url TEXT NOT NULL DEFAULT '',
    qr_payload TEXT NOT NULL DEFAULT '',
    created_at_millis BIGINT NOT NULL,
    expires_at_millis BIGINT NULL,
    paid_at_millis BIGINT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS store_subscription_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL UNIQUE REFERENCES stores(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'inactive',
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    started_at_millis BIGINT NULL,
    current_period_start_millis BIGINT NULL,
    current_period_end_millis BIGINT NULL,
    next_charge_at_millis BIGINT NULL,
    cancelled_at_millis BIGINT NULL,
    past_due_since_millis BIGINT NULL,
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS store_subscription_charge_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code TEXT NOT NULL,
    period_start_millis BIGINT NOT NULL,
    period_end_millis BIGINT NOT NULL,
    status TEXT NOT NULL,
    wallet_ledger_entry_id TEXT NOT NULL DEFAULT '',
    created_at_millis BIGINT NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO user_wallets (id, user_id, currency_code, balance_minor, reserved_minor, updated_at_millis)
SELECT gen_random_uuid(), u.id,
       CASE WHEN lower(u.country_locale) = 'tj' THEN 'TJS' ELSE 'KZT' END,
       0,
       0,
       CAST(EXTRACT(EPOCH FROM NOW()) * 1000 AS BIGINT)
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_wallets w WHERE w.user_id = u.id
);

CREATE INDEX IF NOT EXISTS user_wallet_ledger_user_time_idx
    ON user_wallet_ledger_entries(user_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS top_up_payment_intents_user_time_idx
    ON top_up_payment_intents(user_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS top_up_payment_intents_status_idx
    ON top_up_payment_intents(status, expires_at_millis);

CREATE INDEX IF NOT EXISTS store_subscription_states_status_charge_idx
    ON store_subscription_states(status, auto_renew, next_charge_at_millis);

CREATE INDEX IF NOT EXISTS store_subscription_charge_store_time_idx
    ON store_subscription_charge_events(store_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS transactions_store_time_idx
    ON transactions(store_id, time_millis DESC);

CREATE INDEX IF NOT EXISTS stock_items_store_created_idx
    ON stock_items(store_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_batches_store_goods_created_idx
    ON stock_batches(store_id, goods_item_id, created_at_millis DESC);
