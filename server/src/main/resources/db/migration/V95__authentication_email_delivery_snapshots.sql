-- Immutable encrypted provider requests survive retries and configuration/account changes.
-- Existing queued rows are upgraded lazily by the sender. No secrets or email addresses in plaintext.
ALTER TABLE auth_email_outbox
    ADD COLUMN IF NOT EXISTS payload_ciphertext TEXT NULL;
ALTER TABLE auth_one_time_challenges
    ADD COLUMN IF NOT EXISTS delivery_email_hash CHAR(64) NULL;
