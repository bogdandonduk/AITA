CREATE TABLE stock_item_edit_commands (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request JSONB NOT NULL,
    result JSONB NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX stock_item_edit_commands_store ON stock_item_edit_commands(store_id);
