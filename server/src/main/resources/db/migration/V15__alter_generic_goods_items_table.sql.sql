ALTER TABLE generic_goods_items
  ALTER COLUMN barcode TYPE jsonb USING barcode::jsonb,
  ALTER COLUMN barcode SET DEFAULT null;