WITH ranked_installations AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY user_id, nullif(meta ->> 'installationId', '')
            ORDER BY created_at DESC, id DESC
        ) AS duplicate_rank
    FROM refresh_sessions
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'installationId', '') IS NOT NULL
)
UPDATE refresh_sessions AS sessions
SET revoked_at = CURRENT_TIMESTAMP
FROM ranked_installations
WHERE sessions.id = ranked_installations.id
  AND ranked_installations.duplicate_rank > 1
  AND sessions.revoked_at IS NULL;

DROP INDEX IF EXISTS idx_refresh_sessions_one_active_per_visible_device;

WITH ranked_visible_devices AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY
                user_id,
                nullif(meta ->> 'deviceName', ''),
                coalesce(meta ->> 'platformName', ''),
                coalesce(meta ->> 'osName', '')
            ORDER BY created_at DESC, id DESC
        ) AS duplicate_rank
    FROM refresh_sessions
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'deviceName', '') IS NOT NULL
)
UPDATE refresh_sessions AS sessions
SET revoked_at = CURRENT_TIMESTAMP
FROM ranked_visible_devices
WHERE sessions.id = ranked_visible_devices.id
  AND ranked_visible_devices.duplicate_rank > 1
  AND sessions.revoked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_sessions_one_active_per_visible_device
    ON refresh_sessions(
        user_id,
        (meta ->> 'deviceName'),
        coalesce(meta ->> 'platformName', ''),
        coalesce(meta ->> 'osName', '')
    )
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'deviceName', '') IS NOT NULL;
