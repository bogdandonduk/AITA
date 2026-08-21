ALTER TABLE debtors
  ADD COLUMN IF NOT EXISTS debtor_type TEXT NOT NULL DEFAULT 'individual',
  ADD COLUMN IF NOT EXISTS id_number TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS company_name TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS company_id_number TEXT NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS debt_created_at_millis BIGINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS debt_due_at_millis BIGINT NULL,
  ADD COLUMN IF NOT EXISTS original_debt_amount DOUBLE PRECISION NULL,
  ADD COLUMN IF NOT EXISTS interest JSONB NULL,
  ADD COLUMN IF NOT EXISTS planned_payments JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN IF NOT EXISTS payment_history JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE debtors
SET debt_created_at_millis = FLOOR(EXTRACT(EPOCH FROM created_at) * 1000)::BIGINT
WHERE debt_created_at_millis = 0;

UPDATE debtors
SET original_debt_amount = debt_amount
WHERE original_debt_amount IS NULL;
