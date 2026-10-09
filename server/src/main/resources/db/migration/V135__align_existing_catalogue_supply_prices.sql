-- Owner-requested one-time catalogue price alignment. Historic receipts and batch costs stay immutable.
-- The normal managed release also takes an encrypted full database backup before migrations.
CREATE TABLE stock_supply_price_alignment_backup (
    goods_item_id UUID PRIMARY KEY REFERENCES stock_items(id) ON DELETE CASCADE,
    old_supply_prices JSONB NOT NULL,
    aligned_sale_prices JSONB NOT NULL,
    old_updated_at_millis BIGINT NOT NULL,
    aligned_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO stock_supply_price_alignment_backup(goods_item_id, old_supply_prices, aligned_sale_prices, old_updated_at_millis)
SELECT id, supply_prices, sale_prices, updated_at_millis FROM stock_items
WHERE supply_prices IS DISTINCT FROM sale_prices AND jsonb_array_length(sale_prices) > 0
  AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements(sale_prices) p
      WHERE COALESCE(p->>'currency','') = '' OR COALESCE(p->>'price','') !~ '^[0-9]+([.,][0-9]+)?$');
UPDATE stock_items i SET supply_prices = b.aligned_sale_prices,
    updated_at_millis = GREATEST(i.updated_at_millis + 1, (extract(epoch FROM clock_timestamp()) * 1000)::BIGINT)
FROM stock_supply_price_alignment_backup b WHERE i.id = b.goods_item_id;
