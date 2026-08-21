CREATE TABLE IF NOT EXISTS stock_batch_movements (
    id UUID PRIMARY KEY,
    root_store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    source_store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    destination_store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    source_goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
    destination_goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
    source_batch_id UUID NOT NULL REFERENCES stock_batches(id) ON DELETE CASCADE,
    destination_batch_id UUID NOT NULL REFERENCES stock_batches(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quantity JSONB NOT NULL,
    note TEXT,
    moved_at_millis BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Accepted',
    accepted_by_user_id UUID,
    accepted_at_millis BIGINT,
    decision_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_root_moved
    ON stock_batch_movements(root_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_source_goods
    ON stock_batch_movements(source_goods_item_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_destination_goods
    ON stock_batch_movements(destination_goods_item_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_source_store
    ON stock_batch_movements(source_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_destination_store
    ON stock_batch_movements(destination_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_status
    ON stock_batch_movements(status);
