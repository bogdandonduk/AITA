CREATE TABLE IF NOT EXISTS stock_items (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,

  barcodes JSONB NOT NULL,
  name JSONB NOT NULL,
  description JSONB NOT NULL DEFAULT '[]'::jsonb,

  measurement_unit_id TEXT NOT NULL,
  category_ids JSONB NOT NULL DEFAULT '[]'::jsonb,

  sale_prices JSONB NOT NULL DEFAULT '[]'::jsonb,
  return_prices JSONB NOT NULL DEFAULT '[]'::jsonb,
  supply_prices JSONB NOT NULL DEFAULT '[]'::jsonb,

  is_quick_item BOOLEAN NOT NULL DEFAULT FALSE,
  image_paths JSONB NOT NULL DEFAULT '[]'::jsonb,

  active_shelf_batch_id UUID NULL,

  note TEXT NULL,

  created_at_millis BIGINT NOT NULL,
  updated_at_millis BIGINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_stock_items_user_store
ON stock_items(user_id, store_id);

CREATE INDEX IF NOT EXISTS idx_stock_items_barcodes
ON stock_items USING GIN (barcodes);


CREATE TABLE IF NOT EXISTS stock_batches (
  id UUID PRIMARY KEY,
  goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,

  supplier_id UUID NULL,
  supplier_order_id UUID NULL,

  quantity JSONB NOT NULL,

  supply_price JSONB NOT NULL,
  sale_price_override JSONB NULL,
  return_price_override JSONB NULL,

  delivered_at_millis BIGINT NULL,
  manufactured_at_millis BIGINT NULL,
  expiration_date_millis BIGINT NULL,

  discounts JSONB NOT NULL DEFAULT '[]'::jsonb,

  shelf_position TEXT NULL,
  shelf_priority INTEGER NOT NULL DEFAULT 0,

  status TEXT NOT NULL,

  additional_notes TEXT NULL,

  created_at_millis BIGINT NOT NULL,
  updated_at_millis BIGINT NOT NULL,
  created_by_user_id UUID NULL,

  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_stock_batches_item
ON stock_batches(goods_item_id);

CREATE INDEX IF NOT EXISTS idx_stock_batches_store_supplier
ON stock_batches(store_id, supplier_id);

CREATE INDEX IF NOT EXISTS idx_stock_batches_expiration
ON stock_batches(expiration_date_millis);


CREATE TABLE IF NOT EXISTS supplier_goods_prices (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,
  supplier_id UUID NOT NULL,
  goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,

  supply_price JSONB NOT NULL,

  min_order_quantity JSONB NULL,
  package_quantity JSONB NULL,

  supplier_barcode TEXT NULL,
  supplier_goods_name TEXT NULL,

  last_used_at_millis BIGINT NULL,
  created_at_millis BIGINT NOT NULL,
  updated_at_millis BIGINT NOT NULL,

  is_active BOOLEAN NOT NULL DEFAULT TRUE,

  UNIQUE(store_id, supplier_id, goods_item_id)
);


CREATE TABLE IF NOT EXISTS supplier_orders (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,
  supplier_id UUID NOT NULL,

  amount JSONB NULL,

  ordered_at_millis BIGINT NOT NULL,
  desired_delivery_time_millis BIGINT NULL,
  confirmed_delivery_time_millis BIGINT NULL,
  delivered_at_millis BIGINT NULL,

  store_address JSONB NULL,

  additional_notes TEXT NULL,

  status TEXT NOT NULL,

  created_at_millis BIGINT NOT NULL,
  updated_at_millis BIGINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_supplier_orders_store_supplier
ON supplier_orders(store_id, supplier_id);


CREATE TABLE IF NOT EXISTS supplier_order_lines (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES supplier_orders(id) ON DELETE CASCADE,
  goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,

  requested_quantity JSONB NOT NULL,
  expected_supply_price JSONB NULL,

  desired_expiration_date_millis BIGINT NULL,
  additional_notes TEXT NULL,

  delivered_batch_ids JSONB NOT NULL DEFAULT '[]'::jsonb,

  is_active BOOLEAN NOT NULL DEFAULT TRUE
);