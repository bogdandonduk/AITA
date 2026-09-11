-- Additive upgrade: no existing enrollment, session, or verified contact is deleted.
ALTER TABLE auth_security_profiles ADD COLUMN email_required_for_login boolean NOT NULL DEFAULT false;
ALTER TABLE auth_phone_alias_challenges ADD COLUMN authorization_hash char(64);
-- Old unfinished phone edits did not bind password/security revision. Restart only those edits.
UPDATE auth_one_time_challenges SET consumed_at_millis = (extract(epoch FROM clock_timestamp()) * 1000)::bigint
WHERE purpose = 'PHONE_ALIAS' AND consumed_at_millis IS NULL;

ALTER TABLE auth_one_time_challenges DROP CONSTRAINT auth_one_time_challenges_purpose_check;
ALTER TABLE auth_one_time_challenges ADD CONSTRAINT auth_one_time_challenges_purpose_check
CHECK (purpose IN ('PASSWORDLESS_LOGIN','PASSWORD_RECOVERY','PHONE_ALIAS','EMAIL_ALIAS',
'TOTP_RECOVERY','TOTP_RESET_NOTICE','LOGIN_EMAIL_FACTOR','SECURITY_EMAIL_PROOF'));

CREATE TABLE auth_login_email_challenges (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    login_public_id uuid NOT NULL REFERENCES auth_login_challenges(public_id) ON DELETE CASCADE
);
CREATE INDEX auth_login_email_parent ON auth_login_email_challenges(login_public_id);
CREATE TABLE auth_security_email_challenges (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action varchar(40) NOT NULL CHECK (action IN ('LOGIN_POLICY','ADD_EMAIL','REMOVE_EMAIL','TOTP_SETUP','PROFILE')),
    target_hash char(64) NOT NULL,
    authorization_hash char(64) NOT NULL
);
CREATE INDEX auth_security_email_owner ON auth_security_email_challenges(user_id);

-- Preserve legacy multiple aliases, but forbid any new additional email once one is owned.
-- The user row lock arbitrates concurrent confirmations, including older application versions.
CREATE FUNCTION aita_limit_extra_login_email() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.is_primary THEN RETURN NEW; END IF;
    IF TG_OP = 'UPDATE' AND NOT OLD.is_primary AND OLD.user_id = NEW.user_id THEN RETURN NEW; END IF;
    PERFORM id FROM users WHERE id = NEW.user_id FOR NO KEY UPDATE;
    IF EXISTS (SELECT 1 FROM auth_login_emails WHERE user_id = NEW.user_id AND NOT is_primary
               AND email_normalized <> NEW.email_normalized) THEN
        RAISE EXCEPTION 'Only one extra email is allowed' USING ERRCODE='23505', CONSTRAINT='aita_one_extra_email';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_aita_one_extra_email BEFORE INSERT OR UPDATE ON auth_login_emails
FOR EACH ROW EXECUTE FUNCTION aita_limit_extra_login_email();
