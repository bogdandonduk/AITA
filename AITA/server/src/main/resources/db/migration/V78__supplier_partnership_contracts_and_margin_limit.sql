CREATE TABLE IF NOT EXISTS supplier_partnership_contracts (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_editor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_side TEXT NOT NULL DEFAULT 'supplier',
    scope_type TEXT NOT NULL DEFAULT 'partnership',
    goods_item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    title JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary JSONB NOT NULL DEFAULT '[]'::jsonb,
    conditions JSONB NOT NULL DEFAULT '[]'::jsonb,
    custom_terms JSONB NOT NULL DEFAULT '[]'::jsonb,
    delivery_schedule JSONB NOT NULL DEFAULT '[]'::jsonb,
    payment_schedule JSONB NOT NULL DEFAULT '[]'::jsonb,
    price_terms JSONB NOT NULL DEFAULT '[]'::jsonb,
    status TEXT NOT NULL DEFAULT 'pending_store',
    revision INTEGER NOT NULL DEFAULT 1,
    supplier_accepted_at_millis BIGINT,
    store_accepted_at_millis BIGINT,
    supplier_accepted_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    store_accepted_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    declined_at_millis BIGINT,
    declined_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at_millis BIGINT NOT NULL DEFAULT 0,
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_supplier_partnership_contracts_store_supplier
    ON supplier_partnership_contracts(store_id, supplier_id);

CREATE INDEX IF NOT EXISTS idx_supplier_partnership_contracts_supplier_status
    ON supplier_partnership_contracts(supplier_id, status);

CREATE INDEX IF NOT EXISTS idx_supplier_partnership_contracts_store_status
    ON supplier_partnership_contracts(store_id, status);
