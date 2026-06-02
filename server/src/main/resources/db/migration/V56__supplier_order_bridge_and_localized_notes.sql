CREATE TABLE IF NOT EXISTS supplier_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    store_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    amount JSONB,
    ordered_at_millis BIGINT NOT NULL,
    desired_delivery_time_millis BIGINT,
    confirmed_delivery_time_millis BIGINT,
    delivered_at_millis BIGINT,
    store_address JSONB,
    additional_notes TEXT,
    additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    supplier_comment TEXT,
    supplier_comment_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    payment_terms TEXT,
    external_reference TEXT,
    store_contact_user_id UUID,
    status TEXT NOT NULL DEFAULT 'Draft',
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS supplier_order_lines (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES supplier_orders(id) ON DELETE CASCADE,
    goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
    requested_quantity JSONB NOT NULL,
    expected_supply_price JSONB,
    desired_expiration_date_millis BIGINT,
    additional_notes TEXT,
    additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    supplier_comment TEXT,
    supplier_comment_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    supplier_accepted_quantity JSONB,
    supplier_offered_supply_price JSONB,
    substitute_goods_item_id UUID,
    delivered_batch_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE stock_batches
    ADD COLUMN IF NOT EXISTS additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE supplier_orders
    ADD COLUMN IF NOT EXISTS additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS supplier_comment TEXT,
    ADD COLUMN IF NOT EXISTS supplier_comment_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS payment_terms TEXT,
    ADD COLUMN IF NOT EXISTS external_reference TEXT,
    ADD COLUMN IF NOT EXISTS store_contact_user_id UUID;

ALTER TABLE supplier_order_lines
    ADD COLUMN IF NOT EXISTS additional_notes_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS supplier_comment TEXT,
    ADD COLUMN IF NOT EXISTS supplier_comment_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS supplier_accepted_quantity JSONB,
    ADD COLUMN IF NOT EXISTS supplier_offered_supply_price JSONB,
    ADD COLUMN IF NOT EXISTS substitute_goods_item_id UUID;

UPDATE supplier_orders
SET additional_notes_localized = '[]'::jsonb
WHERE additional_notes_localized IS NULL;

UPDATE supplier_order_lines
SET additional_notes_localized = '[]'::jsonb
WHERE additional_notes_localized IS NULL;

UPDATE stock_batches
SET additional_notes_localized = '[]'::jsonb
WHERE additional_notes_localized IS NULL;

CREATE INDEX IF NOT EXISTS idx_supplier_orders_store_active
    ON supplier_orders(store_id, is_active);

CREATE INDEX IF NOT EXISTS idx_supplier_orders_supplier
    ON supplier_orders(supplier_id);

CREATE INDEX IF NOT EXISTS idx_supplier_orders_status
    ON supplier_orders(status);

CREATE INDEX IF NOT EXISTS idx_supplier_order_lines_order_active
    ON supplier_order_lines(order_id, is_active);

CREATE INDEX IF NOT EXISTS idx_supplier_order_lines_goods_item
    ON supplier_order_lines(goods_item_id);
