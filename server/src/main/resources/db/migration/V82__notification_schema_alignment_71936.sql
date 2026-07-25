-- Align Flyway schema with the notification table shape used by the server at startup.
-- Startup cleanup and notification replacement logic read these columns before optional schema auto-repair can run.

ALTER TABLE user_notifications
    ADD COLUMN IF NOT EXISTS store_id UUID NULL REFERENCES stores(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE user_notifications
SET is_active = TRUE
WHERE is_active IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_notifications_store_created
    ON user_notifications(store_id, created_at_millis DESC)
    WHERE store_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_active_created
    ON user_notifications(user_id, is_active, created_at_millis DESC);
