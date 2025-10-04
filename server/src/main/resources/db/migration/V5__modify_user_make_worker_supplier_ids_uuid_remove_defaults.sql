ALTER TABLE users
  ADD COLUMN IF NOT EXISTS store_worker_account_id uuid,
  ADD COLUMN IF NOT EXISTS store_supplier_account_id uuid;