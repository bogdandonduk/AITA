-- V76__grant_stock_history_view_permission.sql
-- Add the new stock-history permission to existing worker permission sets and defaults.

ALTER TABLE store_worker_memberships
    ALTER COLUMN permissions SET DEFAULT '["sale_transaction","return_transaction","transaction_history_view","cash_register_view","stock_read","stock_history_view","debtors_view","debtor_payments_manage"]'::jsonb;

ALTER TABLE store_worker_requests
    ALTER COLUMN permissions SET DEFAULT '["sale_transaction","return_transaction","transaction_history_view","cash_register_view","stock_read","stock_history_view","debtors_view","debtor_payments_manage"]'::jsonb;

ALTER TABLE store_worker_role_templates
    ALTER COLUMN permissions SET DEFAULT '["sale_transaction","return_transaction","transaction_history_view","cash_register_view","stock_read","stock_history_view","debtors_view","debtor_payments_manage"]'::jsonb;

UPDATE store_worker_memberships
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["stock_history_view"]'::jsonb
WHERE NOT COALESCE(permissions, '[]'::jsonb) ? 'stock_history_view'
  AND (
      COALESCE(permissions, '[]'::jsonb) ? 'stock_read'
      OR COALESCE(permissions, '[]'::jsonb) ? 'stock_write'
      OR COALESCE(permissions, '[]'::jsonb) ? 'logs_view'
  );

UPDATE store_worker_requests
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["stock_history_view"]'::jsonb
WHERE NOT COALESCE(permissions, '[]'::jsonb) ? 'stock_history_view'
  AND (
      COALESCE(permissions, '[]'::jsonb) ? 'stock_read'
      OR COALESCE(permissions, '[]'::jsonb) ? 'stock_write'
      OR COALESCE(permissions, '[]'::jsonb) ? 'logs_view'
  );

UPDATE store_worker_role_templates
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["stock_history_view"]'::jsonb
WHERE NOT COALESCE(permissions, '[]'::jsonb) ? 'stock_history_view'
  AND (
      COALESCE(permissions, '[]'::jsonb) ? 'stock_read'
      OR COALESCE(permissions, '[]'::jsonb) ? 'stock_write'
      OR COALESCE(permissions, '[]'::jsonb) ? 'logs_view'
  );
