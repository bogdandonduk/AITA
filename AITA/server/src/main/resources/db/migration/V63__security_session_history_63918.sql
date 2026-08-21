CREATE TABLE IF NOT EXISTS security_session_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_id UUID NULL,
    event_type TEXT NOT NULL,
    title JSONB NOT NULL DEFAULT '[]'::jsonb,
    details JSONB NOT NULL DEFAULT '[]'::jsonb,
    device_name TEXT NOT NULL DEFAULT '',
    platform_name TEXT NOT NULL DEFAULT '',
    os_name TEXT NOT NULL DEFAULT '',
    app_name TEXT NOT NULL DEFAULT '',
    app_version TEXT NOT NULL DEFAULT '',
    ip_address TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at_millis BIGINT NOT NULL DEFAULT ((extract(epoch from now()) * 1000)::bigint),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_session_events_user_created
    ON security_session_events(user_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_security_session_events_session_id
    ON security_session_events(session_id);

CREATE INDEX IF NOT EXISTS idx_security_session_events_event_type
    ON security_session_events(event_type);

WITH ranked AS (
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
FROM ranked
WHERE sessions.id = ranked.id
  AND ranked.duplicate_rank > 1
  AND sessions.revoked_at IS NULL;

WITH ranked AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY
                user_id,
                nullif(meta ->> 'deviceName', ''),
                nullif(meta ->> 'platformName', ''),
                nullif(meta ->> 'osName', '')
            ORDER BY created_at DESC, id DESC
        ) AS duplicate_rank
    FROM refresh_sessions
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'installationId', '') IS NULL
      AND nullif(meta ->> 'deviceName', '') IS NOT NULL
)
UPDATE refresh_sessions AS sessions
SET revoked_at = CURRENT_TIMESTAMP
FROM ranked
WHERE sessions.id = ranked.id
  AND ranked.duplicate_rank > 1
  AND sessions.revoked_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_sessions_one_active_per_installation
    ON refresh_sessions(user_id, (meta ->> 'installationId'))
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'installationId', '') IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_sessions_one_active_per_visible_device
    ON refresh_sessions(
        user_id,
        (meta ->> 'deviceName'),
        (meta ->> 'platformName'),
        (meta ->> 'osName')
    )
    WHERE revoked_at IS NULL
      AND nullif(meta ->> 'installationId', '') IS NULL
      AND nullif(meta ->> 'deviceName', '') IS NOT NULL;
