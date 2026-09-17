-- An old private upload must not become visible to colleagues merely by upgrading.
CREATE TABLE user_mode_profile_photos (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('STORE', 'SUPPLIER', 'MARKETPLACE')),
    revision BIGINT NOT NULL CHECK (revision >= 1),
    jpeg BYTEA CHECK (jpeg IS NULL OR octet_length(jpeg) BETWEEN 1 AND 524288),
    updated_at_millis BIGINT NOT NULL CHECK (updated_at_millis >= 0),
    PRIMARY KEY (user_id, mode)
);
-- Keep the legacy personal picture in the non-shared Marketplace profile only.
INSERT INTO user_mode_profile_photos (user_id, mode, revision, jpeg, updated_at_millis)
SELECT user_id, 'MARKETPLACE', revision, jpeg, updated_at_millis FROM user_profile_photos;
-- Legacy routes now alias the Marketplace slot. Retain the old table but not a hidden copy of removed images.
UPDATE user_profile_photos SET jpeg = NULL WHERE jpeg IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transactions_store_actor_time ON transactions (store_id, user_id, time_millis);
CREATE INDEX IF NOT EXISTS idx_operation_logs_store_actor_time ON operation_logs (store_id, actor_user_id, created_at_millis);
