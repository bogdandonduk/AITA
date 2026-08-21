-- Defensive schema repair for supplier/store integration passes.
-- Some dev databases were created before wholesale stock columns and the newer stock-batch shape existed.
-- Keeping this as a new migration avoids changing already-applied Flyway checksums.

ALTER TABLE stock_items
    ADD COLUMN IF NOT EXISTS wholesale_prices JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS wholesale_min_quantity JSONB,
    ADD COLUMN IF NOT EXISTS note_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE stock_items
SET wholesale_prices = '[]'::jsonb
WHERE wholesale_prices IS NULL;

UPDATE stock_items
SET note_localized = '[]'::jsonb
WHERE note_localized IS NULL;

ALTER TABLE stock_batches
    ADD COLUMN IF NOT EXISTS supplier_order_id UUID,
    ADD COLUMN IF NOT EXISTS sale_price_override JSONB,
    ADD COLUMN IF NOT EXISTS return_price_override JSONB,
    ADD COLUMN IF NOT EXISTS wholesale_price_override JSONB,
    ADD COLUMN IF NOT EXISTS promotions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE stock_batches
SET promotions = '[]'::jsonb
WHERE promotions IS NULL;

UPDATE stock_batches
SET additional_notes_localized = '[]'::jsonb
WHERE additional_notes_localized IS NULL;

CREATE INDEX IF NOT EXISTS idx_stock_batches_supplier_order
    ON stock_batches(supplier_order_id);

CREATE INDEX IF NOT EXISTS idx_stock_batches_store_goods_active
    ON stock_batches(store_id, goods_item_id, is_active);
