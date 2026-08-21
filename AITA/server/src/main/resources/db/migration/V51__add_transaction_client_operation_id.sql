ALTER TABLE transactions ADD COLUMN IF NOT EXISTS client_operation_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS transactions_client_operation_id_unique
    ON transactions (client_operation_id)
    WHERE client_operation_id IS NOT NULL AND client_operation_id <> '';
