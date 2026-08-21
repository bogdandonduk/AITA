CREATE TABLE IF NOT EXISTS generic_goods_item_candidates (
    id UUID PRIMARY KEY,
    normalized_barcode TEXT NOT NULL,
    barcode TEXT NOT NULL,
    common_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_user_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_stock_item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    submission_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'waiting',
    promoted_generic_goods_item_id UUID NULL,
    created_at_millis BIGINT NOT NULL DEFAULT 0,
    updated_at_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_generic_goods_item_candidates_barcode_status
    ON generic_goods_item_candidates (normalized_barcode, status);

CREATE INDEX IF NOT EXISTS idx_generic_goods_item_candidates_status
    ON generic_goods_item_candidates (status);

CREATE INDEX IF NOT EXISTS idx_generic_goods_item_candidates_promoted_item
    ON generic_goods_item_candidates (promoted_generic_goods_item_id);
