-- Additive account recovery; do not edit an applied migration.
-- Existing enrolled users KEEP their current mandatory-login policy.
-- A terminal revocation must never be resurrected by legacy refresh-recovery logic.
ALTER TABLE refresh_sessions ADD COLUMN security_invalidated boolean NOT NULL DEFAULT false;
UPDATE refresh_sessions SET security_invalidated = true WHERE revoked_at IS NOT NULL;

ALTER TABLE auth_security_profiles ADD COLUMN totp_last_used_step bigint;
ALTER TABLE auth_login_challenges ADD COLUMN authorization_hash char(64);
ALTER TABLE auth_login_challenges DROP CONSTRAINT auth_login_challenges_primary_method_check;
ALTER TABLE auth_login_challenges ADD CONSTRAINT auth_login_challenges_primary_method_check
    CHECK (primary_method IN ('PASSWORD', 'EMAIL_CODE', 'AUTHENTICATOR'));

-- Pre-upgrade half-finished logins have no credential/revision binding. Restart only these
-- short-lived challenges; this does NOT revoke already authenticated sessions.
UPDATE auth_login_challenges SET consumed_at_millis = (extract(epoch FROM clock_timestamp()) * 1000)::bigint
WHERE consumed_at_millis IS NULL;

ALTER TABLE auth_one_time_challenges DROP CONSTRAINT auth_one_time_challenges_purpose_check;
ALTER TABLE auth_one_time_challenges ADD CONSTRAINT auth_one_time_challenges_purpose_check
    CHECK (purpose IN ('PASSWORDLESS_LOGIN', 'PASSWORD_RECOVERY', 'PHONE_ALIAS', 'EMAIL_ALIAS',
                       'TOTP_RECOVERY', 'TOTP_RESET_NOTICE'));

CREATE TABLE auth_totp_recovery_challenges (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    authorization_hash char(64) NOT NULL,
    created_at_millis bigint NOT NULL
);
CREATE INDEX auth_totp_recovery_owner ON auth_totp_recovery_challenges(user_id);

CREATE INDEX auth_totp_attempt_identifier ON auth_security_audit_events(identifier_hash, created_at_millis)
    WHERE event_type = 'AUTHENTICATOR_LOGIN_ATTEMPT';
CREATE INDEX auth_totp_attempt_ip ON auth_security_audit_events(ip_hash, created_at_millis)
    WHERE event_type = 'AUTHENTICATOR_LOGIN_ATTEMPT';
