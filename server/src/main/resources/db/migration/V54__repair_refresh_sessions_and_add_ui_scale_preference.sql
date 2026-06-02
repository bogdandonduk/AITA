-- Repair refresh-session storage after token-rotation hardening and add the UI-scale account preference.
-- This migration is intentionally idempotent for development databases that may have partial prior columns.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS app_language VARCHAR(16) NOT NULL DEFAULT 'ru';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS app_theme_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS app_size_mode_id BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET app_language = 'ru'
WHERE app_language IS NULL OR TRIM(app_language) = '' OR app_language NOT IN ('system', 'en', 'ru', 'kk');

UPDATE users
SET app_theme_id = 0
WHERE app_theme_id IS NULL OR app_theme_id NOT IN (0, 1);

UPDATE users
SET app_size_mode_id = 0
WHERE app_size_mode_id IS NULL OR app_size_mode_id NOT IN (0, 1);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'refresh_sessions'
    ) THEN
        ALTER TABLE refresh_sessions
            ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP NULL;

        ALTER TABLE refresh_sessions
            ADD COLUMN IF NOT EXISTS rotated_from UUID NULL;

        ALTER TABLE refresh_sessions
            ADD COLUMN IF NOT EXISTS meta JSONB NULL;

        -- Old sessions may have short TTLs or pre-rotation hashes. Clearing them removes the broken login loop.
        TRUNCATE TABLE refresh_sessions;
    END IF;
END $$;
