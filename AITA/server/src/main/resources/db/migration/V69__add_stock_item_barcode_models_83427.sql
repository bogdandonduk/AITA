ALTER TABLE stock_items
    ADD COLUMN IF NOT EXISTS barcode_models jsonb;

UPDATE stock_items
SET barcode_models = '[]'::jsonb
WHERE barcode_models IS NULL;

ALTER TABLE stock_items
    ALTER COLUMN barcode_models SET DEFAULT '[]'::jsonb,
    ALTER COLUMN barcode_models SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_items_barcode_models_gin
    ON stock_items USING GIN (barcode_models);
