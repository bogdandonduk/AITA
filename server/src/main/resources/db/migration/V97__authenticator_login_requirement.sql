-- Enrollment and requiring a second factor during login are separate settings.
-- Preserve protection for ALL existing accounts; never silently downgrade enrolled users.
ALTER TABLE auth_security_profiles
    ADD COLUMN totp_required_for_login boolean NOT NULL DEFAULT true;
