DO $$
BEGIN
    IF to_regclass('public.refresh_sessions') IS NOT NULL THEN
        UPDATE refresh_sessions
        SET expires_at = TIMESTAMP '9999-12-31 23:59:59'
        WHERE revoked_at IS NULL
          AND expires_at < TIMESTAMP '9999-01-01 00:00:00';
    END IF;
END $$;
