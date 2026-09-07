-- AITA advanced authentication: email OTP, password recovery, TOTP and recovery codes.
-- Additive only. Do not edit after it has been applied.

CREATE TABLE IF NOT EXISTS auth_security_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_verified_at_millis BIGINT NULL,
    phone_login_alias VARCHAR(32) NULL,
    phone_alias_verified_at_millis BIGINT NULL,
    totp_secret_ciphertext TEXT NULL,
    totp_pending_secret_ciphertext TEXT NULL,
    totp_pending_setup_id UUID NULL,
    totp_pending_expires_at_millis BIGINT NULL,
    totp_enabled_at_millis BIGINT NULL,
    security_revision BIGINT NOT NULL DEFAULT 1 CHECK (security_revision >= 1),
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_security_profiles_phone_login_alias
    ON auth_security_profiles(phone_login_alias)
    WHERE phone_login_alias IS NOT NULL;

CREATE TABLE IF NOT EXISTS auth_one_time_challenges (
    id UUID PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL CHECK (purpose IN ('PASSWORDLESS_LOGIN', 'PASSWORD_RECOVERY', 'PHONE_ALIAS')),
    identifier_hash CHAR(64) NOT NULL,
    request_ip_hash CHAR(64) NOT NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'en',
    code_hash CHAR(64) NOT NULL,
    code_ciphertext TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 3 AND 10),
    expires_at_millis BIGINT NOT NULL,
    resend_after_millis BIGINT NOT NULL,
    verified_at_millis BIGINT NULL,
    reset_ticket_hash CHAR(64) NULL,
    reset_ticket_expires_at_millis BIGINT NULL,
    consumed_at_millis BIGINT NULL,
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_one_time_challenges_identifier_created
    ON auth_one_time_challenges(identifier_hash, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_auth_one_time_challenges_ip_created
    ON auth_one_time_challenges(request_ip_hash, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_auth_one_time_challenges_expiry
    ON auth_one_time_challenges(expires_at_millis);
CREATE INDEX IF NOT EXISTS idx_auth_one_time_challenges_user_purpose
    ON auth_one_time_challenges(user_id, purpose, created_at_millis DESC);

CREATE TABLE IF NOT EXISTS auth_email_outbox (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL UNIQUE REFERENCES auth_one_time_challenges(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'SENT', 'FAILED', 'CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 8 CHECK (max_attempts BETWEEN 1 AND 20),
    next_attempt_at_millis BIGINT NOT NULL,
    locked_at_millis BIGINT NULL,
    locked_by VARCHAR(120) NULL,
    provider_message_id TEXT NULL,
    last_error_code VARCHAR(80) NULL,
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    sent_at_millis BIGINT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_email_outbox_claim
    ON auth_email_outbox(status, next_attempt_at_millis, created_at_millis);

CREATE TABLE IF NOT EXISTS auth_login_challenges (
    id UUID PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    primary_method VARCHAR(32) NOT NULL CHECK (primary_method IN ('PASSWORD', 'EMAIL_CODE')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 8 CHECK (max_attempts BETWEEN 3 AND 20),
    expires_at_millis BIGINT NOT NULL,
    consumed_at_millis BIGINT NULL,
    created_at_millis BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_login_challenges_user_expiry
    ON auth_login_challenges(user_id, expires_at_millis DESC);

CREATE TABLE IF NOT EXISTS auth_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,
    created_at_millis BIGINT NOT NULL,
    used_at_millis BIGINT NULL,
    UNIQUE(user_id, code_hash)
);

CREATE INDEX IF NOT EXISTS idx_auth_recovery_codes_available
    ON auth_recovery_codes(user_id, used_at_millis);

CREATE TABLE IF NOT EXISTS auth_security_audit_events (
    id UUID PRIMARY KEY,
    user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(80) NOT NULL,
    identifier_hash CHAR(64) NULL,
    ip_hash CHAR(64) NULL,
    metadata TEXT NOT NULL DEFAULT '{}',
    created_at_millis BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_security_audit_user_time
    ON auth_security_audit_events(user_id, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_auth_security_audit_type_time
    ON auth_security_audit_events(event_type, created_at_millis DESC);
