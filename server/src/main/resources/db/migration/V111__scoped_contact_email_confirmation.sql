-- New contacts require a scoped proof; existing contacts/accounts are deliberately not re-labelled.
ALTER TABLE auth_one_time_challenges DROP CONSTRAINT auth_one_time_challenges_purpose_check;
ALTER TABLE auth_one_time_challenges ADD CONSTRAINT auth_one_time_challenges_purpose_check
CHECK (purpose IN ('PASSWORDLESS_LOGIN','PASSWORD_RECOVERY','PHONE_ALIAS','EMAIL_ALIAS',
'TOTP_RECOVERY','TOTP_RESET_NOTICE','LOGIN_EMAIL_FACTOR','SECURITY_EMAIL_PROOF',
'CONTACT_VERIFICATION','ACCOUNT_EMAIL_CHANGE_NOTICE'));

CREATE TABLE auth_contact_verifications (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    actor_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
    channel varchar(16) NOT NULL CHECK (channel = 'EMAIL'),
    contact_purpose varchar(32) NOT NULL CHECK (contact_purpose IN ('REGISTRATION','ACCOUNT_CONTACT','STORE_CONTACT','SUPPLIER_CONTACT')),
    entity_id varchar(80) NOT NULL,
    parent_id varchar(36) NOT NULL DEFAULT '',
    address varchar(254) NOT NULL CHECK (address = lower(btrim(address)) AND address <> ''),
    scope_hash char(64) NOT NULL,
    authorization_hash char(64),
    receipt_hash char(64),
    receipt_ciphertext text,
    receipt_expires_at_millis bigint,
    applied_entity_id uuid,
    applied_at_millis bigint,
    created_at_millis bigint NOT NULL,
    CHECK ((applied_entity_id IS NULL AND applied_at_millis IS NULL) OR
           (applied_entity_id IS NOT NULL AND applied_at_millis IS NOT NULL)),
    CHECK ((contact_purpose = 'REGISTRATION' AND actor_user_id IS NULL) OR
           (contact_purpose <> 'REGISTRATION' AND actor_user_id IS NOT NULL)),
    CHECK ((receipt_hash IS NULL AND receipt_ciphertext IS NULL AND receipt_expires_at_millis IS NULL) OR
           (receipt_hash IS NOT NULL AND receipt_ciphertext IS NOT NULL AND receipt_expires_at_millis IS NOT NULL))
);
CREATE INDEX auth_contact_verification_scope ON auth_contact_verifications(scope_hash, created_at_millis DESC);
CREATE INDEX auth_contact_verification_applied ON auth_contact_verifications(contact_purpose, applied_entity_id, address);
CREATE INDEX auth_contact_verification_actor ON auth_contact_verifications(actor_user_id, created_at_millis DESC);

-- Immutable old-recipient notices are separate from proof-bearing contacts and can never confirm one.
CREATE TABLE auth_contact_change_notices (
    challenge_public_id uuid PRIMARY KEY REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address varchar(254) NOT NULL,
    created_at_millis bigint NOT NULL
);
