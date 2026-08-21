-- V45: stock branch / parent-store batch movement ledger
-- A batch can be moved inside one store entity: parent warehouse <-> branches <-> sibling branches.

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
    note TEXT NULL,
    moved_at_millis BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS stock_batch_movements_root_time_idx
    ON stock_batch_movements(root_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_batch_movements_source_store_idx
    ON stock_batch_movements(source_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_batch_movements_destination_store_idx
    ON stock_batch_movements(destination_store_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_batch_movements_source_goods_idx
    ON stock_batch_movements(source_goods_item_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_batch_movements_destination_goods_idx
    ON stock_batch_movements(destination_goods_item_id, moved_at_millis DESC);

CREATE INDEX IF NOT EXISTS stock_items_store_active_idx
    ON stock_items(store_id, is_active);

CREATE INDEX IF NOT EXISTS stock_batches_store_goods_active_idx
    ON stock_batches(store_id, goods_item_id, is_active);
