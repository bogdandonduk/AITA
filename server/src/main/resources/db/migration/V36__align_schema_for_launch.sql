-- AITA local launch schema alignment.
-- Use this on a fresh/local database after the old prototype migrations.
-- It intentionally recreates the app-owned tables to match the current Server.kt table objects.
-- Do NOT run this on a production database with valuable data unless you intentionally want a clean reset.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- Drop dependent/newer tables first. This keeps flyway_schema_history intact.
DROP TABLE IF EXISTS supplier_order_lines CASCADE;
DROP TABLE IF EXISTS supplier_orders CASCADE;
DROP TABLE IF EXISTS supplier_goods_prices CASCADE;
DROP TABLE IF EXISTS stock_batches CASCADE;
DROP TABLE IF EXISTS stock_items CASCADE;
DROP TABLE IF EXISTS stock CASCADE;
DROP TABLE IF EXISTS transactions CASCADE;
DROP TABLE IF EXISTS debtors CASCADE;
DROP TABLE IF EXISTS realtime_updates CASCADE;
DROP TABLE IF EXISTS store_users CASCADE;
DROP TABLE IF EXISTS store_subscriptions CASCADE;
DROP TABLE IF EXISTS store_activation_history CASCADE;
DROP TABLE IF EXISTS user_balances CASCADE;
DROP TABLE IF EXISTS generic_goods_categories CASCADE;
DROP TABLE IF EXISTS generic_goods_items CASCADE;
DROP TABLE IF EXISTS manufacturers CASCADE;
DROP TABLE IF EXISTS suppliers CASCADE;
DROP TABLE IF EXISTS stores CASCADE;
DROP TABLE IF EXISTS refresh_sessions CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS store_workers CASCADE;
DROP TABLE IF EXISTS store_suppliers CASCADE;

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number VARCHAR(32) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  country_locale VARCHAR(64) NOT NULL,
  worker_ids TEXT NULL DEFAULT NULL,
  supplier_ids TEXT NULL DEFAULT NULL,
  active_store_id UUID NULL,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE refresh_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  rotated_from UUID NULL,
  revoked_at TIMESTAMP NULL,
  meta JSONB NULL
);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user_id ON refresh_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_token_hash ON refresh_sessions(token_hash);

CREATE TABLE stores (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  store_type_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  name JSONB NOT NULL DEFAULT '[]'::jsonb,
  alias JSONB NOT NULL DEFAULT '[]'::jsonb,
  description JSONB NOT NULL DEFAULT '[]'::jsonb,
  company_forms JSONB NOT NULL DEFAULT '[]'::jsonb,
  location JSONB NOT NULL DEFAULT '{}'::jsonb,
  phone_numbers JSONB NOT NULL DEFAULT '[]'::jsonb,
  emails JSONB NOT NULL DEFAULT '[]'::jsonb,
  country_locales JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE store_users (
  store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (store_id, user_id)
);

CREATE TABLE store_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID UNIQUE NOT NULL,
  history JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE store_activation_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID UNIQUE NOT NULL,
  history JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE user_balances (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID UNIQUE NOT NULL,
  value TEXT NOT NULL,
  currency_code TEXT NOT NULL,
  history JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE suppliers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_ids TEXT NULL DEFAULT NULL,
  type_ids TEXT NULL DEFAULT NULL,
  category_ids TEXT NULL DEFAULT NULL,
  name TEXT NOT NULL,
  phone_numbers TEXT NULL DEFAULT NULL,
  emails TEXT NULL DEFAULT NULL,
  added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE manufacturers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_ids TEXT NULL DEFAULT NULL,
  type_ids TEXT NULL DEFAULT NULL,
  category_ids TEXT NULL DEFAULT NULL,
  name TEXT NOT NULL,
  phone_numbers TEXT NULL DEFAULT NULL,
  emails TEXT NULL DEFAULT NULL,
  added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE generic_goods_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  barcode JSONB NOT NULL DEFAULT '[]'::jsonb,
  name TEXT NOT NULL,
  type_ids TEXT NULL DEFAULT NULL,
  category_ids TEXT NULL DEFAULT NULL,
  supplier_ids TEXT NULL DEFAULT NULL,
  manufacturer_ids TEXT NULL DEFAULT NULL
);

CREATE TABLE generic_goods_categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  name JSONB NOT NULL DEFAULT '[]'::jsonb,
  quantity_unit_id TEXT NOT NULL,
  image_paths JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE realtime_updates (
  "userId" UUID PRIMARY KEY,
  "updateIds" JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE stock_items (
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
  generic_expiration_period JSONB NULL,
  is_quick_item BOOLEAN NOT NULL DEFAULT FALSE,
  image_paths JSONB NOT NULL DEFAULT '[]'::jsonb,
  active_shelf_batch_id UUID NULL,
  note TEXT NULL,
  created_at_millis BIGINT NOT NULL,
  updated_at_millis BIGINT NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_stock_items_user_store ON stock_items(user_id, store_id);
CREATE INDEX IF NOT EXISTS idx_stock_items_barcodes ON stock_items USING GIN (barcodes);

CREATE TABLE stock_batches (
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
CREATE INDEX IF NOT EXISTS idx_stock_batches_item ON stock_batches(goods_item_id);
CREATE INDEX IF NOT EXISTS idx_stock_batches_store_supplier ON stock_batches(store_id, supplier_id);
CREATE INDEX IF NOT EXISTS idx_stock_batches_expiration ON stock_batches(expiration_date_millis);

CREATE TABLE supplier_goods_prices (
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

CREATE TABLE supplier_orders (
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
CREATE INDEX IF NOT EXISTS idx_supplier_orders_store_supplier ON supplier_orders(store_id, supplier_id);

CREATE TABLE supplier_order_lines (
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

CREATE TABLE transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  workshift_id BIGINT NOT NULL DEFAULT 0,
  type TEXT NOT NULL,
  store_id UUID NOT NULL,
  goods_in_transaction JSONB NOT NULL,
  paid_cash DOUBLE PRECISION NOT NULL DEFAULT 0,
  paid_card DOUBLE PRECISION NOT NULL DEFAULT 0,
  card_payment_option_id INTEGER NOT NULL DEFAULT 0,
  debtor TEXT NULL,
  time_millis BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_transactions_user_store ON transactions(user_id, store_id);
CREATE INDEX IF NOT EXISTS idx_transactions_time ON transactions(time_millis);

CREATE TABLE debtors (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,
  email TEXT NOT NULL DEFAULT '',
  debt_amount DOUBLE PRECISION NOT NULL,
  currency TEXT NOT NULL,
  phone_number TEXT NOT NULL DEFAULT '',
  first_name TEXT NOT NULL,
  last_name TEXT NOT NULL,
  transaction_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_debtors_user_store ON debtors(user_id, store_id);
CREATE INDEX IF NOT EXISTS idx_debtors_phone ON debtors(phone_number);
