-- Extend V93's closed purpose set; never edit the already-applied migration.
ALTER TABLE auth_one_time_challenges DROP CONSTRAINT auth_one_time_challenges_purpose_check;
ALTER TABLE auth_one_time_challenges ADD CONSTRAINT auth_one_time_challenges_purpose_check
    CHECK (purpose IN ('PASSWORDLESS_LOGIN', 'PASSWORD_RECOVERY', 'PHONE_ALIAS', 'EMAIL_ALIAS'));

-- One unique namespace for primary and confirmed additional email identities. Pending
-- additions live separately and can never be resolved as a login identity.
CREATE TABLE auth_login_emails (
    email_normalized varchar(254) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_primary boolean NOT NULL DEFAULT false,
    verified_at_millis bigint,
    created_at_millis bigint NOT NULL,
    CONSTRAINT auth_login_emails_normalized CHECK (email_normalized = lower(btrim(email_normalized)) AND email_normalized <> ''),
    CONSTRAINT auth_login_emails_alias_verified CHECK (is_primary OR verified_at_millis IS NOT NULL)
);
CREATE UNIQUE INDEX auth_login_emails_one_primary_per_user ON auth_login_emails(user_id) WHERE is_primary;
CREATE INDEX auth_login_emails_owner ON auth_login_emails(user_id);

-- Fail safely rather than silently choosing an account when legacy primary emails collide.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE btrim(email) <> '' GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'AITA: duplicate normalized primary email identities; resolve the conflicting accounts before applying V96';
    END IF;
END $$;
INSERT INTO auth_login_emails(email_normalized, user_id, is_primary, created_at_millis)
SELECT lower(btrim(email)), id, true, (extract(epoch FROM clock_timestamp()) * 1000)::bigint
FROM users WHERE btrim(email) <> '';

-- This protects ALL registration/profile writers, including older clients and direct SQL.
-- A single PK arbitrates concurrent primary-registration / alias-confirmation races.
CREATE FUNCTION aita_sync_primary_login_email() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    normalized text := lower(btrim(NEW.email));
BEGIN
    IF TG_OP = 'UPDATE' AND lower(btrim(OLD.email)) = normalized THEN RETURN NEW; END IF;
    DELETE FROM auth_login_emails WHERE user_id = NEW.id AND is_primary;
    IF normalized <> '' THEN
        INSERT INTO auth_login_emails(email_normalized, user_id, is_primary, created_at_millis)
        VALUES(normalized, NEW.id, true, (extract(epoch FROM clock_timestamp()) * 1000)::bigint)
        ON CONFLICT(email_normalized) DO UPDATE SET is_primary = true
            WHERE auth_login_emails.user_id = NEW.id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Email identity is unavailable' USING ERRCODE = '23505';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        UPDATE auth_security_profiles SET email_verified_at_millis = NULL,
            security_revision = security_revision + 1,
            updated_at_millis = (extract(epoch FROM clock_timestamp()) * 1000)::bigint
        WHERE user_id = NEW.id;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_aita_primary_login_email
    AFTER INSERT OR UPDATE OF email ON users
    FOR EACH ROW EXECUTE FUNCTION aita_sync_primary_login_email();

CREATE TABLE auth_email_alias_challenges (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_email varchar(254) NOT NULL,
    authorization_hash char(64) NOT NULL,
    consumed_at_millis bigint,
    created_at_millis bigint NOT NULL
);
CREATE INDEX auth_email_alias_challenges_owner ON auth_email_alias_challenges(user_id);
