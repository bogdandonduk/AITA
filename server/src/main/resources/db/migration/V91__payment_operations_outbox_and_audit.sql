-- Normalize the foundation tables for the durable orchestration layer. ADD COLUMN IF NOT EXISTS
-- keeps this migration safe when an earlier environment already contains the field.
ALTER TABLE aita_balance_accounts
    ADD COLUMN IF NOT EXISTS available_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

ALTER TABLE aita_balance_invoices
    ADD COLUMN IF NOT EXISTS environment VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION',
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS return_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS provider_invoice_id VARCHAR(240),
    ADD COLUMN IF NOT EXISTS payment_url VARCHAR(4096),
    ADD COLUMN IF NOT EXISTS qr_payload TEXT,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS credited_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS safe_error_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS safe_error_message VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS aita_balance_invoices_store_idempotency_idx
    ON aita_balance_invoices (store_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE aita_balance_ledger_entries
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(240),
    ADD COLUMN IF NOT EXISTS balance_after_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reference_id VARCHAR(200);

ALTER TABLE fiscal_receipts
    ADD COLUMN IF NOT EXISTS transaction_reference VARCHAR(200),
    ADD COLUMN IF NOT EXISTS operation VARCHAR(20),
    ADD COLUMN IF NOT EXISTS state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(180),
    ADD COLUMN IF NOT EXISTS request_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS original_receipt_id UUID,
    ADD COLUMN IF NOT EXISTS provider_receipt_id VARCHAR(240),
    ADD COLUMN IF NOT EXISTS fiscal_document_number VARCHAR(240),
    ADD COLUMN IF NOT EXISTS fiscal_sign VARCHAR(512),
    ADD COLUMN IF NOT EXISTS receipt_url VARCHAR(4096),
    ADD COLUMN IF NOT EXISTS provider_response JSONB,
    ADD COLUMN IF NOT EXISTS safe_error_code VARCHAR(160),
    ADD COLUMN IF NOT EXISTS safe_error_message VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS fiscalized_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS fiscal_receipts_store_idempotency_idx
    ON fiscal_receipts (store_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_provider_operation_outbox (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(40) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    operation_kind VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    request_payload JSONB NOT NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(160),
    last_http_status INTEGER,
    last_safe_error_code VARCHAR(160),
    last_safe_error_message VARCHAR(1024),
    provider_reference VARCHAR(240),
    provider_response JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT payment_provider_operation_outbox_attempt_nonnegative CHECK (attempt_count >= 0),
    CONSTRAINT payment_provider_operation_outbox_state_check CHECK (
        state IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT payment_provider_operation_outbox_unique_idempotency
        UNIQUE (store_id, provider, environment, idempotency_key)
);

CREATE INDEX IF NOT EXISTS payment_provider_operation_outbox_due_idx
    ON payment_provider_operation_outbox (next_attempt_at, created_at)
    WHERE state IN ('PENDING', 'RETRY_WAIT');

CREATE INDEX IF NOT EXISTS payment_provider_operation_outbox_aggregate_idx
    ON payment_provider_operation_outbox (aggregate_type, aggregate_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_integration_audit_events (
    id UUID PRIMARY KEY,
    store_id UUID,
    actor_user_id UUID,
    provider VARCHAR(40),
    environment VARCHAR(20),
    action VARCHAR(120) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(180),
    outcome VARCHAR(30) NOT NULL,
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_integration_audit_outcome_check CHECK (
        outcome IN ('SUCCEEDED', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS payment_integration_audit_store_time_idx
    ON payment_integration_audit_events (store_id, created_at DESC);

CREATE INDEX IF NOT EXISTS payment_integration_audit_provider_time_idx
    ON payment_integration_audit_events (provider, created_at DESC);

-- Defense in depth for the balance ledger. The foundation migration already treats entries as
-- immutable; these indexes make replay checks cheap under concurrent callback and polling paths.
CREATE UNIQUE INDEX IF NOT EXISTS aita_balance_ledger_invoice_once_idx
    ON aita_balance_ledger_entries (reference_type, reference_id)
    WHERE reference_type = 'BALANCE_INVOICE';

CREATE UNIQUE INDEX IF NOT EXISTS aita_balance_ledger_provider_event_once_idx
    ON aita_balance_ledger_entries (provider_event_id)
    WHERE provider_event_id IS NOT NULL;
