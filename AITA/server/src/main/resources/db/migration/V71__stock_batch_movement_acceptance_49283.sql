ALTER TABLE stock_batch_movements
    ADD COLUMN IF NOT EXISTS status TEXT,
    ADD COLUMN IF NOT EXISTS accepted_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS accepted_at_millis BIGINT,
    ADD COLUMN IF NOT EXISTS decision_note TEXT;

UPDATE stock_batch_movements
SET status = 'Accepted'
WHERE status IS NULL OR status = '';

UPDATE stock_batch_movements
SET accepted_by_user_id = COALESCE(accepted_by_user_id, user_id),
    accepted_at_millis = COALESCE(accepted_at_millis, moved_at_millis)
WHERE status = 'Accepted';

ALTER TABLE stock_batch_movements
    ALTER COLUMN status SET DEFAULT 'Accepted',
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_batch_movements_status_destination
    ON stock_batch_movements(status, destination_store_id);
