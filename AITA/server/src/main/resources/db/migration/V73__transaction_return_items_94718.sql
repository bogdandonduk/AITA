CREATE TABLE IF NOT EXISTS transaction_return_items (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  user_id UUID NOT NULL,
  store_id UUID NOT NULL,
  line_index INTEGER NOT NULL DEFAULT 0,
  goods_item_id UUID NULL,
  barcode TEXT NOT NULL DEFAULT '',
  name JSONB NOT NULL DEFAULT '[]'::jsonb,
  quantity DOUBLE PRECISION NOT NULL DEFAULT 0,
  price_per_unit DOUBLE PRECISION NOT NULL DEFAULT 0,
  currency_code TEXT NULL,
  return_reason TEXT NOT NULL DEFAULT '',
  client_operation_id TEXT NULL,
  time_millis BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_transaction_return_items_transaction_line
ON transaction_return_items(transaction_id, line_index);

CREATE INDEX IF NOT EXISTS idx_transaction_return_items_store_time
ON transaction_return_items(store_id, time_millis);

CREATE INDEX IF NOT EXISTS idx_transaction_return_items_goods_item
ON transaction_return_items(goods_item_id);

CREATE INDEX IF NOT EXISTS idx_transaction_return_items_reason
ON transaction_return_items(store_id, return_reason);

WITH expanded AS (
  SELECT
    t.id AS transaction_id,
    t.user_id AS user_id,
    t.store_id AS store_id,
    (line.ordinality - 1)::INTEGER AS line_index,
    line.line_value AS line_value,
    t.client_operation_id AS client_operation_id,
    t.time_millis AS time_millis,
    t.created_at AS created_at,
    md5(t.id::TEXT || ':' || (line.ordinality - 1)::TEXT) AS digest
  FROM transactions t
  CROSS JOIN LATERAL jsonb_array_elements(t.goods_in_transaction) WITH ORDINALITY AS line(line_value, ordinality)
  WHERE t.type = 'return'
)
INSERT INTO transaction_return_items (
  id,
  transaction_id,
  user_id,
  store_id,
  line_index,
  goods_item_id,
  barcode,
  name,
  quantity,
  price_per_unit,
  currency_code,
  return_reason,
  client_operation_id,
  time_millis,
  created_at
)
SELECT
  (
    substr(digest, 1, 8) || '-' ||
    substr(digest, 9, 4) || '-' ||
    substr(digest, 13, 4) || '-' ||
    substr(digest, 17, 4) || '-' ||
    substr(digest, 21, 12)
  )::UUID AS id,
  transaction_id,
  user_id,
  store_id,
  line_index,
  CASE
    WHEN COALESCE(line_value ->> 'goodsItemId', '') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      THEN (line_value ->> 'goodsItemId')::UUID
    ELSE NULL
  END AS goods_item_id,
  COALESCE(line_value ->> 'barcode', '') AS barcode,
  COALESCE(line_value -> 'name', '[]'::jsonb) AS name,
  COALESCE(NULLIF(line_value ->> 'quantity', '')::DOUBLE PRECISION, 0) AS quantity,
  COALESCE(NULLIF(line_value ->> 'pricePerUnit', '')::DOUBLE PRECISION, 0) AS price_per_unit,
  NULLIF(line_value ->> 'currencyCode', '') AS currency_code,
  LEFT(BTRIM(COALESCE(line_value ->> 'returnReason', '')), 500) AS return_reason,
  NULLIF(client_operation_id, '') AS client_operation_id,
  time_millis,
  created_at
FROM expanded
ON CONFLICT DO NOTHING;
