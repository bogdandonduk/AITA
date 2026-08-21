-- V43: Public short IDs for user/store invitation UX, reverse worker invites,
-- and safety backfill for stock item creation timestamps used by stock sorting.

ALTER TABLE users ADD COLUMN IF NOT EXISTS public_id VARCHAR(16);
ALTER TABLE stores ADD COLUMN IF NOT EXISTS public_id VARCHAR(16);

DO $$
DECLARE
    r RECORD;
    candidate TEXT;
BEGIN
    FOR r IN SELECT id FROM users WHERE public_id IS NULL OR public_id = '' LOOP
        LOOP
            candidate := 'U' || upper(substr(md5(random()::text || clock_timestamp()::text || r.id::text), 1, 8));
            EXIT WHEN NOT EXISTS (SELECT 1 FROM users WHERE public_id = candidate);
        END LOOP;
        UPDATE users SET public_id = candidate WHERE id = r.id;
    END LOOP;

    FOR r IN SELECT id FROM stores WHERE public_id IS NULL OR public_id = '' LOOP
        LOOP
            candidate := 'S' || upper(substr(md5(random()::text || clock_timestamp()::text || r.id::text), 1, 8));
            EXIT WHEN NOT EXISTS (SELECT 1 FROM stores WHERE public_id = candidate);
        END LOOP;
        UPDATE stores SET public_id = candidate WHERE id = r.id;
    END LOOP;
END $$;

ALTER TABLE users ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE stores ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS users_public_id_uq ON users(public_id);
CREATE UNIQUE INDEX IF NOT EXISTS stores_public_id_uq ON stores(public_id);
CREATE INDEX IF NOT EXISTS users_public_id_lookup_idx ON users(public_id);
CREATE INDEX IF NOT EXISTS stores_public_id_lookup_idx ON stores(public_id);

ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS direction TEXT NOT NULL DEFAULT 'user_to_store';

ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS invited_by_user_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'store_worker_requests_invited_by_user_fk'
    ) THEN
        ALTER TABLE store_worker_requests
            ADD CONSTRAINT store_worker_requests_invited_by_user_fk
            FOREIGN KEY (invited_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

UPDATE store_worker_requests
SET direction = 'user_to_store'
WHERE direction IS NULL OR direction = '';

CREATE INDEX IF NOT EXISTS store_worker_requests_direction_idx ON store_worker_requests(direction);
CREATE INDEX IF NOT EXISTS store_worker_requests_invited_by_user_id_idx ON store_worker_requests(invited_by_user_id);

-- Make sure older databases have usable stock creation timestamps for sorting.
ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS created_at_millis BIGINT;
ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS updated_at_millis BIGINT;

UPDATE stock_items
SET created_at_millis = COALESCE(NULLIF(created_at_millis, 0), (extract(epoch FROM now()) * 1000)::BIGINT)
WHERE created_at_millis IS NULL OR created_at_millis = 0;

UPDATE stock_items
SET updated_at_millis = COALESCE(NULLIF(updated_at_millis, 0), created_at_millis, (extract(epoch FROM now()) * 1000)::BIGINT)
WHERE updated_at_millis IS NULL OR updated_at_millis = 0;

CREATE INDEX IF NOT EXISTS stock_items_store_created_at_idx ON stock_items(store_id, created_at_millis);
