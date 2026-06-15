ALTER TABLE IF EXISTS stock_batches
    ADD COLUMN IF NOT EXISTS supplier_order_id UUID,
    ADD COLUMN IF NOT EXISTS promotions JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE IF EXISTS stock_batches
    ALTER COLUMN supplier_id DROP NOT NULL,
    ALTER COLUMN supplier_order_id DROP NOT NULL;

UPDATE stock_batches
SET promotions = '[]'::jsonb
WHERE promotions IS NULL;

UPDATE stock_batches
SET additional_notes_localized = '[]'::jsonb
WHERE additional_notes_localized IS NULL;

ALTER TABLE IF EXISTS stock_batches
    ALTER COLUMN promotions SET DEFAULT '[]'::jsonb,
    ALTER COLUMN promotions SET NOT NULL,
    ALTER COLUMN additional_notes_localized SET DEFAULT '[]'::jsonb,
    ALTER COLUMN additional_notes_localized SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_batches_supplier_order_stability
    ON stock_batches(supplier_order_id);

CREATE INDEX IF NOT EXISTS idx_stock_batches_store_supplier_stability
    ON stock_batches(store_id, supplier_id);
