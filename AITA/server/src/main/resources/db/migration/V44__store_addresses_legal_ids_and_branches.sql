-- Adds store address/legal identity fields and store branch support.
-- Parent store rows keep the legal identity. Branch rows point to a parent store and carry their own physical address.

ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS parent_store_id UUID NULL,
    ADD COLUMN IF NOT EXISTS address TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS legal_id_type_id TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS legal_id TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

UPDATE stores
SET address = COALESCE(NULLIF(location ->> 'name', ''), address, '')
WHERE address IS NULL OR address = '';

UPDATE stores
SET country_locales = CASE
    WHEN country_locales IS NULL OR country_locales::text = 'null' OR country_locales::text = '[]' THEN '["kz"]'::jsonb
    ELSE country_locales
END;

UPDATE stores
SET legal_id_type_id = CASE
    WHEN legal_id_type_id IS NULL OR legal_id_type_id = '' THEN
        CASE
            WHEN country_locales::text ILIKE '%"tj"%' THEN 'tj_tin'
            ELSE 'kz_bin'
        END
    ELSE legal_id_type_id
END
WHERE parent_store_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'stores_parent_store_id_fkey'
    ) THEN
        ALTER TABLE stores
            ADD CONSTRAINT stores_parent_store_id_fkey
            FOREIGN KEY (parent_store_id)
            REFERENCES stores(id)
            ON DELETE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS stores_parent_store_id_idx ON stores(parent_store_id);
CREATE INDEX IF NOT EXISTS stores_legal_id_idx ON stores(legal_id_type_id, legal_id);
CREATE INDEX IF NOT EXISTS stores_address_idx ON stores USING gin (to_tsvector('simple', address));

-- Only parent stores should be unique by legal identity. Empty legacy IDs are intentionally ignored.
CREATE UNIQUE INDEX IF NOT EXISTS stores_parent_legal_id_unique_idx
    ON stores(legal_id_type_id, legal_id)
    WHERE parent_store_id IS NULL AND legal_id IS NOT NULL AND legal_id <> '';
