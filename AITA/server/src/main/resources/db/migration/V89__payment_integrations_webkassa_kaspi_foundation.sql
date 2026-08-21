-- Secure per-store Webkassa and Kaspi Pay integration foundation.
-- Provider secrets are encrypted by the application before insertion.
-- Monetary values are stored in minor units to avoid floating-point drift.

CREATE TABLE IF NOT EXISTS payment_provider_credentials (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    public_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    encrypted_secret BYTEA NOT NULL,
    secret_nonce BYTEA NOT NULL,
    key_version INTEGER NOT NULL,
    health VARCHAR(24) NOT NULL DEFAULT 'CONFIGURED',
    merchant_reference_masked VARCHAR(128),
    cashbox_reference_masked VARCHAR(128),
    last_verified_at TIMESTAMPTZ,
    last_successful_call_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    last_error_code VARCHAR(128),
    last_error_message TEXT,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_provider_credentials_provider_ck
        CHECK (provider IN ('WEBKASSA', 'KASPI_PAY')),
    CONSTRAINT payment_provider_credentials_environment_ck
        CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT payment_provider_credentials_health_ck
        CHECK (health IN ('DISCONNECTED', 'CONFIGURED', 'VERIFYING', 'ACTIVE', 'DEGRADED', 'REVOKED')),
    CONSTRAINT payment_provider_credentials_store_provider_uq
        UNIQUE (store_id, provider, environment)
);

CREATE INDEX IF NOT EXISTS payment_provider_credentials_store_idx
    ON payment_provider_credentials (store_id, provider);

CREATE TABLE IF NOT EXISTS aita_balance_accounts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL UNIQUE,
    currency CHAR(3) NOT NULL DEFAULT 'KZT',
    available_minor BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT aita_balance_accounts_currency_ck
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT aita_balance_accounts_available_ck
        CHECK (available_minor >= 0)
);

CREATE TABLE IF NOT EXISTS aita_balance_invoices (
    id UUID PRIMARY KEY,
    balance_account_id UUID NOT NULL REFERENCES aita_balance_accounts(id),
    store_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    external_invoice_id VARCHAR(256),
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    description TEXT,
    payment_url TEXT,
    qr_payload TEXT,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code VARCHAR(128),
    failure_message TEXT,
    provider_payload JSONB,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT aita_balance_invoices_provider_ck
        CHECK (provider = 'KASPI_PAY'),
    CONSTRAINT aita_balance_invoices_environment_ck
        CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT aita_balance_invoices_status_ck
        CHECK (status IN ('CREATING', 'AWAITING_PAYMENT', 'PAID', 'EXPIRED', 'CANCELLED', 'FAILED', 'REFUNDED')),
    CONSTRAINT aita_balance_invoices_amount_ck
        CHECK (amount_minor > 0),
    CONSTRAINT aita_balance_invoices_currency_ck
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT aita_balance_invoices_store_idempotency_uq
        UNIQUE (store_id, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS aita_balance_invoices_external_uq
    ON aita_balance_invoices (provider, environment, external_invoice_id)
    WHERE external_invoice_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS aita_balance_invoices_reconcile_idx
    ON aita_balance_invoices (provider, environment, status, updated_at)
    WHERE status IN ('CREATING', 'AWAITING_PAYMENT');

CREATE TABLE IF NOT EXISTS aita_balance_ledger_entries (
    id UUID PRIMARY KEY,
    balance_account_id UUID NOT NULL REFERENCES aita_balance_accounts(id),
    store_id UUID NOT NULL,
    entry_type VARCHAR(24) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    balance_after_minor BIGINT NOT NULL,
    reference_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(256) NOT NULL,
    idempotency_key VARCHAR(192) NOT NULL,
    description TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT aita_balance_ledger_type_ck
        CHECK (entry_type IN ('TOP_UP', 'CHARGE', 'REFUND', 'MANUAL_ADJUSTMENT', 'REVERSAL')),
    CONSTRAINT aita_balance_ledger_currency_ck
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT aita_balance_ledger_balance_ck
        CHECK (balance_after_minor >= 0),
    CONSTRAINT aita_balance_ledger_idempotency_uq
        UNIQUE (balance_account_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS aita_balance_ledger_store_created_idx
    ON aita_balance_ledger_entries (store_id, created_at DESC);

CREATE TABLE IF NOT EXISTS fiscal_receipts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    transaction_id VARCHAR(160),
    provider VARCHAR(32) NOT NULL DEFAULT 'WEBKASSA',
    environment VARCHAR(16) NOT NULL,
    receipt_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(192) NOT NULL,
    external_receipt_id VARCHAR(256),
    original_receipt_id UUID REFERENCES fiscal_receipts(id),
    total_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    fiscal_number VARCHAR(256),
    check_number VARCHAR(256),
    fiscal_sign VARCHAR(512),
    ticket_url TEXT,
    request_payload JSONB NOT NULL,
    provider_payload JSONB,
    failure_code VARCHAR(128),
    failure_message TEXT,
    registered_at TIMESTAMPTZ,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fiscal_receipts_provider_ck
        CHECK (provider = 'WEBKASSA'),
    CONSTRAINT fiscal_receipts_environment_ck
        CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT fiscal_receipts_type_ck
        CHECK (receipt_type IN ('SALE', 'SALE_RETURN', 'CORRECTION', 'CASH_IN', 'CASH_OUT')),
    CONSTRAINT fiscal_receipts_status_ck
        CHECK (status IN ('PENDING', 'REGISTERED', 'FAILED', 'CANCELLED')),
    CONSTRAINT fiscal_receipts_total_ck
        CHECK (total_minor > 0),
    CONSTRAINT fiscal_receipts_currency_ck
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT fiscal_receipts_store_idempotency_uq
        UNIQUE (store_id, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS fiscal_receipts_external_uq
    ON fiscal_receipts (provider, environment, external_receipt_id)
    WHERE external_receipt_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS fiscal_receipts_transaction_idx
    ON fiscal_receipts (store_id, transaction_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_provider_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    external_event_id VARCHAR(256),
    payload_sha256 CHAR(64) NOT NULL,
    signature_valid BOOLEAN NOT NULL DEFAULT FALSE,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    request_headers JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_payload JSONB NOT NULL,
    failure_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT payment_provider_webhook_provider_ck
        CHECK (provider IN ('WEBKASSA', 'KASPI_PAY')),
    CONSTRAINT payment_provider_webhook_environment_ck
        CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT payment_provider_webhook_status_ck
        CHECK (processing_status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS payment_provider_webhook_external_uq
    ON payment_provider_webhook_events (provider, environment, external_event_id)
    WHERE external_event_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS payment_provider_webhook_payload_uq
    ON payment_provider_webhook_events (provider, environment, payload_sha256);
