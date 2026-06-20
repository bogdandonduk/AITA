-- Align Flyway schema with server store-access filtering that treats stores as active/inactive.
-- V36 recreated stores without this legacy column; newer server code filters on it for safer cloud launch access.

ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN;

UPDATE stores
SET is_active = TRUE
WHERE is_active IS NULL;

ALTER TABLE stores
    ALTER COLUMN is_active SET DEFAULT TRUE,
    ALTER COLUMN is_active SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stores_active_parent
    ON stores(is_active, parent_store_id);
