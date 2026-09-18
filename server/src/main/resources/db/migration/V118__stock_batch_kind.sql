ALTER TABLE stock_batches ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'NORMAL';
ALTER TABLE stock_batches ADD CONSTRAINT stock_batches_kind_check
    CHECK (kind IN ('NORMAL', 'RETURNED', 'UNIVERSAL'));

UPDATE stock_batches SET kind = 'RETURNED'
WHERE kind = 'NORMAL' AND additional_notes = 'aita_returned_no_stock_batch';

CREATE INDEX IF NOT EXISTS idx_stock_batches_special_kind
    ON stock_batches (store_id, goods_item_id, kind) WHERE is_active AND kind <> 'NORMAL';

ALTER TABLE transaction_return_items ADD COLUMN IF NOT EXISTS original_transaction_id UUID;
ALTER TABLE transaction_return_items ADD COLUMN IF NOT EXISTS original_transaction_line_index INTEGER;
ALTER TABLE transaction_return_items ADD CONSTRAINT transaction_return_items_original_line_check
    CHECK ((original_transaction_id IS NULL AND original_transaction_line_index IS NULL)
        OR (original_transaction_id IS NOT NULL AND original_transaction_line_index IS NOT NULL AND original_transaction_line_index >= 0));
CREATE INDEX IF NOT EXISTS idx_transaction_return_items_original_line
    ON transaction_return_items (original_transaction_id, original_transaction_line_index)
    WHERE original_transaction_id IS NOT NULL;
