-- Public global templates only. Store inventory and community templates are unchanged.
-- Install indexes while the packaged catalogue is still small, before any expanded import.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS open_goods_search_trgm ON open_goods_catalogue USING gin(search_text gin_trgm_ops);
CREATE INDEX IF NOT EXISTS open_goods_category_ids ON open_goods_catalogue USING gin((payload::jsonb -> 'categoryIds'));
CREATE INDEX IF NOT EXISTS open_goods_gtin_key ON open_goods_catalogue((lpad(code,14,'0')));
CREATE INDEX IF NOT EXISTS open_goods_name_prefix ON open_goods_catalogue(sort_name text_pattern_ops);
