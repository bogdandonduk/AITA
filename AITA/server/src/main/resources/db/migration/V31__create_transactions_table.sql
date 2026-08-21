CREATE TABLE IF NOT EXISTS transactions (
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

CREATE INDEX IF NOT EXISTS idx_transactions_user_store
ON transactions(user_id, store_id);

CREATE INDEX IF NOT EXISTS idx_transactions_time
ON transactions(time_millis);