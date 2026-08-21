UPDATE generic_goods_items SET barcode = '[]'::jsonb WHERE barcode IS NULL;

ALTER TABLE generic_goods_items
  ALTER COLUMN barcode TYPE jsonb USING barcode::jsonb,
  ALTER COLUMN barcode SET DEFAULT '[]'::jsonb,
  ALTER COLUMN barcode SET NOT NULL;

