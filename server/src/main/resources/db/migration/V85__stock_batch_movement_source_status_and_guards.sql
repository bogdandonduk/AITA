ALTER TABLE stock_batch_movements
    ADD COLUMN IF NOT EXISTS source_status_before_move TEXT;

UPDATE stock_batch_movements
SET source_status_before_move = 'Delivered'
WHERE source_status_before_move IS NULL OR BTRIM(source_status_before_move) = '';

ALTER TABLE stock_batch_movements
    ALTER COLUMN source_status_before_move SET DEFAULT 'Delivered',
    ALTER COLUMN source_status_before_move SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_pending_destination_store
    ON stock_batch_movements (destination_store_id, moved_at_millis DESC)
    WHERE status = 'PendingAcceptance';

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_pending_source_batch
    ON stock_batch_movements (source_batch_id)
    WHERE status = 'PendingAcceptance';

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_pending_destination_batch
    ON stock_batch_movements (destination_batch_id)
    WHERE status = 'PendingAcceptance';
