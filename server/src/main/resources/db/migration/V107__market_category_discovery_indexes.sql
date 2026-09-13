-- Read-only public discovery: filters are applied before sorting/limiting.
-- Names and descriptions remain literal searches; no private inventory text is indexed here.
CREATE INDEX marketplace_listings_public_recent
    ON marketplace_listings(created_at_millis DESC,id) WHERE is_published;
CREATE INDEX marketplace_listings_public_title
    ON marketplace_listings(lower(title),id) WHERE is_published;
CREATE INDEX marketplace_stock_category_membership
    ON stock_items USING GIN ((category_ids::jsonb)) WHERE is_active;
