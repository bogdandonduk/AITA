-- V75__store_worker_role_templates_and_granular_permissions.sql
-- Granular worker permissions + reusable worker role templates.

CREATE TABLE IF NOT EXISTS store_worker_role_templates (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name JSONB NOT NULL DEFAULT '[]'::jsonb,
    description JSONB NOT NULL DEFAULT '[]'::jsonb,
    permissions JSONB NOT NULL DEFAULT '["sale_transaction","return_transaction","transaction_history_view","cash_register_view","stock_read","debtors_view","debtor_payments_manage"]'::jsonb,
    created_at_millis BIGINT NOT NULL DEFAULT 0,
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_store_worker_role_templates_store_active_updated
    ON store_worker_role_templates(store_id, is_active, updated_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_store_worker_role_templates_store_name
    ON store_worker_role_templates(store_id);

UPDATE store_worker_memberships swm
SET permissions = COALESCE((
    SELECT jsonb_agg(permission ORDER BY sort_order)
    FROM (
        SELECT permission, MIN(sort_order) AS sort_order
        FROM (
            SELECT
                value AS permission,
                array_position(ARRAY[
                    'sale_transaction',
                    'return_transaction',
                    'supply_transaction',
                    'transaction_history_view',
                    'cash_register_view',
                    'cash_register_extract',
                    'stock_read',
                    'stock_item_create',
                    'stock_item_edit',
                    'stock_item_delete',
                    'stock_batch_create',
                    'stock_batch_edit',
                    'stock_batch_delete',
                    'stock_batch_move',
                    'stock_batch_transfer_decide',
                    'stock_batch_set_active_shelf',
                    'stock_promotions_manage',
                    'suppliers_view',
                    'suppliers_manage',
                    'supplier_prices_manage',
                    'supplier_orders_view',
                    'supplier_orders_manage',
                    'supplier_orders_receive',
                    'debtors_view',
                    'debtors_manage',
                    'debtor_payments_manage',
                    'analytics_view',
                    'logs_view',
                    'workers_view',
                    'workers_invite',
                    'workers_decide_requests',
                    'workers_edit_permissions',
                    'workers_remove',
                    'worker_role_templates_manage',
                    'store_manage',
                    'branches_manage',
                    'subscription_manage'
                ]::text[], value) AS sort_order
            FROM jsonb_array_elements_text(
                COALESCE(swm.permissions, '[]'::jsonb)
                || CASE WHEN COALESCE(swm.permissions, '[]'::jsonb) ? 'stock_write' THEN '[
                    "stock_read",
                    "stock_item_create",
                    "stock_item_edit",
                    "stock_item_delete",
                    "stock_batch_create",
                    "stock_batch_edit",
                    "stock_batch_delete",
                    "stock_batch_move",
                    "stock_batch_transfer_decide",
                    "stock_batch_set_active_shelf",
                    "stock_promotions_manage",
                    "suppliers_view",
                    "supplier_prices_manage",
                    "supplier_orders_view",
                    "supplier_orders_manage",
                    "supplier_orders_receive"
                ]'::jsonb ELSE '[]'::jsonb END
                || CASE WHEN COALESCE(swm.permissions, '[]'::jsonb) ? 'workers_manage' THEN '[
                    "workers_view",
                    "workers_invite",
                    "workers_decide_requests",
                    "workers_edit_permissions",
                    "workers_remove",
                    "worker_role_templates_manage"
                ]'::jsonb ELSE '[]'::jsonb END
            ) AS permissions(value)
        ) normalized
        WHERE sort_order IS NOT NULL
        GROUP BY permission
    ) ordered_permissions
), '[]'::jsonb);

UPDATE store_worker_requests swr
SET permissions = COALESCE((
    SELECT jsonb_agg(permission ORDER BY sort_order)
    FROM (
        SELECT permission, MIN(sort_order) AS sort_order
        FROM (
            SELECT
                value AS permission,
                array_position(ARRAY[
                    'sale_transaction',
                    'return_transaction',
                    'supply_transaction',
                    'transaction_history_view',
                    'cash_register_view',
                    'cash_register_extract',
                    'stock_read',
                    'stock_item_create',
                    'stock_item_edit',
                    'stock_item_delete',
                    'stock_batch_create',
                    'stock_batch_edit',
                    'stock_batch_delete',
                    'stock_batch_move',
                    'stock_batch_transfer_decide',
                    'stock_batch_set_active_shelf',
                    'stock_promotions_manage',
                    'suppliers_view',
                    'suppliers_manage',
                    'supplier_prices_manage',
                    'supplier_orders_view',
                    'supplier_orders_manage',
                    'supplier_orders_receive',
                    'debtors_view',
                    'debtors_manage',
                    'debtor_payments_manage',
                    'analytics_view',
                    'logs_view',
                    'workers_view',
                    'workers_invite',
                    'workers_decide_requests',
                    'workers_edit_permissions',
                    'workers_remove',
                    'worker_role_templates_manage',
                    'store_manage',
                    'branches_manage',
                    'subscription_manage'
                ]::text[], value) AS sort_order
            FROM jsonb_array_elements_text(
                COALESCE(swr.permissions, '[]'::jsonb)
                || CASE WHEN COALESCE(swr.permissions, '[]'::jsonb) ? 'stock_write' THEN '[
                    "stock_read",
                    "stock_item_create",
                    "stock_item_edit",
                    "stock_item_delete",
                    "stock_batch_create",
                    "stock_batch_edit",
                    "stock_batch_delete",
                    "stock_batch_move",
                    "stock_batch_transfer_decide",
                    "stock_batch_set_active_shelf",
                    "stock_promotions_manage",
                    "suppliers_view",
                    "supplier_prices_manage",
                    "supplier_orders_view",
                    "supplier_orders_manage",
                    "supplier_orders_receive"
                ]'::jsonb ELSE '[]'::jsonb END
                || CASE WHEN COALESCE(swr.permissions, '[]'::jsonb) ? 'workers_manage' THEN '[
                    "workers_view",
                    "workers_invite",
                    "workers_decide_requests",
                    "workers_edit_permissions",
                    "workers_remove",
                    "worker_role_templates_manage"
                ]'::jsonb ELSE '[]'::jsonb END
            ) AS permissions(value)
        ) normalized
        WHERE sort_order IS NOT NULL
        GROUP BY permission
    ) ordered_permissions
), '[]'::jsonb);

UPDATE store_worker_memberships
SET permissions = '[
    "sale_transaction",
    "return_transaction",
    "supply_transaction",
    "transaction_history_view",
    "cash_register_view",
    "cash_register_extract",
    "stock_read",
    "stock_item_create",
    "stock_item_edit",
    "stock_item_delete",
    "stock_batch_create",
    "stock_batch_edit",
    "stock_batch_delete",
    "stock_batch_move",
    "stock_batch_transfer_decide",
    "stock_batch_set_active_shelf",
    "stock_promotions_manage",
    "suppliers_view",
    "suppliers_manage",
    "supplier_prices_manage",
    "supplier_orders_view",
    "supplier_orders_manage",
    "supplier_orders_receive",
    "debtors_view",
    "debtors_manage",
    "debtor_payments_manage",
    "analytics_view",
    "logs_view",
    "workers_view",
    "workers_invite",
    "workers_decide_requests",
    "workers_edit_permissions",
    "workers_remove",
    "worker_role_templates_manage",
    "store_manage",
    "branches_manage",
    "subscription_manage"
]'::jsonb
WHERE role_id = 'admin';

UPDATE store_worker_requests
SET permissions = '[
    "sale_transaction",
    "return_transaction",
    "supply_transaction",
    "transaction_history_view",
    "cash_register_view",
    "cash_register_extract",
    "stock_read",
    "stock_item_create",
    "stock_item_edit",
    "stock_item_delete",
    "stock_batch_create",
    "stock_batch_edit",
    "stock_batch_delete",
    "stock_batch_move",
    "stock_batch_transfer_decide",
    "stock_batch_set_active_shelf",
    "stock_promotions_manage",
    "suppliers_view",
    "suppliers_manage",
    "supplier_prices_manage",
    "supplier_orders_view",
    "supplier_orders_manage",
    "supplier_orders_receive",
    "debtors_view",
    "debtors_manage",
    "debtor_payments_manage",
    "analytics_view",
    "logs_view",
    "workers_view",
    "workers_invite",
    "workers_decide_requests",
    "workers_edit_permissions",
    "workers_remove",
    "worker_role_templates_manage",
    "store_manage",
    "branches_manage",
    "subscription_manage"
]'::jsonb
WHERE role_id = 'admin';
