-- Generic browser/OS names identify a device class, not one installation. Preserve every
-- session and security revocation; do not revive tokens revoked by an earlier release.
DROP INDEX IF EXISTS idx_refresh_sessions_one_active_per_visible_device;
CREATE UNIQUE INDEX idx_refresh_sessions_one_active_per_visible_device
    ON refresh_sessions(user_id, (meta ->> 'deviceName'),
        coalesce(meta ->> 'platformName', ''), coalesce(meta ->> 'osName', ''))
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'installationId', '') IS NULL
      AND nullif(meta ->> 'deviceName', '') IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_live_user_created
    ON refresh_sessions(user_id, created_at DESC)
    WHERE revoked_at IS NULL AND security_invalidated = FALSE;
