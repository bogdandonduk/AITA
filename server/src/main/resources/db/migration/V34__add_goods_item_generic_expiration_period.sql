ALTER TABLE stock_items
  ADD COLUMN IF NOT EXISTS generic_expiration_period JSONB NULL;
