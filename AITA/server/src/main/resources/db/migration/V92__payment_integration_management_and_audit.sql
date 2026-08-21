CREATE TABLE IF NOT EXISTS payment_integration_profile_metadata (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(48) NOT NULL,
    environment VARCHAR(24) NOT NULL,
    display_name VARCHAR(160),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision > 0),
    verification_state VARCHAR(48) NOT NULL DEFAULT 'CONFIGURED_UNVERIFIED',
    settings_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_verified_at TIMESTAMPTZ,
    last_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_integration_profile_identity_unique UNIQUE (store_id, provider, environment),
    CONSTRAINT payment_integration_profile_environment_check CHECK (environment IN ('TEST', 'PRODUCTION')),
    CONSTRAINT payment_integration_profile_provider_check CHECK (provider IN ('WEBKASSA', 'KASPI_PAY')),
    CONSTRAINT payment_integration_profile_settings_object_check CHECK (jsonb_typeof(settings_json) = 'object')
);

CREATE INDEX IF NOT EXISTS payment_integration_profile_store_idx
    ON payment_integration_profile_metadata (store_id, enabled, provider, environment);

CREATE TABLE IF NOT EXISTS payment_integration_audit_events (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    provider VARCHAR(48) NOT NULL,
    environment VARCHAR(24) NOT NULL,
    actor_subject VARCHAR(256) NOT NULL,
    action VARCHAR(48) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    safe_error_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_integration_audit_provider_check CHECK (provider IN ('WEBKASSA', 'KASPI_PAY')),
    CONSTRAINT payment_integration_audit_environment_check CHECK (environment IN ('TEST', 'PRODUCTION'))
);

CREATE INDEX IF NOT EXISTS payment_integration_audit_store_created_idx
    ON payment_integration_audit_events (store_id, created_at DESC);

COMMENT ON TABLE payment_integration_profile_metadata IS
    'Non-secret per-Store payment-provider profile metadata. Secret values remain encrypted in payment_provider_credentials.';
COMMENT ON TABLE payment_integration_audit_events IS
    'Safe payment-integration audit trail. This table must never contain tokens, passwords, private keys, or raw provider payloads.';
