-- Private inventory never becomes public merely by installing an update.
-- Every physical location and every product requires explicit owner publication.
CREATE TABLE marketplace_storefronts (
    store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT '' CHECK (char_length(display_name)<=120),
    city TEXT NOT NULL DEFAULT '' CHECK (char_length(city)<=100),
    public_address TEXT NOT NULL DEFAULT '' CHECK (char_length(public_address)<=400),
    pickup_note TEXT NOT NULL DEFAULT '' CHECK (char_length(pickup_note)<=1000),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision>0),
    updated_by UUID NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    CHECK (NOT is_published OR (length(trim(display_name))>0 AND length(trim(city))>0 AND length(trim(public_address))>0))
);
CREATE INDEX marketplace_storefronts_public_city ON marketplace_storefronts(lower(city),store_id) WHERE is_published;
CREATE TABLE marketplace_listings (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES marketplace_storefronts(store_id) ON DELETE CASCADE,
    goods_item_id UUID NOT NULL REFERENCES stock_items(id) ON DELETE CASCADE,
    title TEXT NOT NULL CHECK (char_length(title) BETWEEN 1 AND 180),
    description TEXT NOT NULL DEFAULT '' CHECK (char_length(description)<=2000),
    gtin TEXT CHECK (gtin ~ '^[0-9]{14}$'),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1 CHECK (revision>0),
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    updated_by UUID NOT NULL,
    UNIQUE(store_id,goods_item_id)
);
CREATE INDEX marketplace_listings_public ON marketplace_listings(id,store_id) WHERE is_published;
CREATE INDEX marketplace_listings_gtin ON marketplace_listings(gtin,id) WHERE is_published AND gtin IS NOT NULL;
CREATE TABLE buyer_saved_offers (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY(user_id,listing_id)
);
CREATE TABLE marketplace_publication_events (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL,
    listing_id UUID,
    actor_user_id UUID NOT NULL,
    session_id UUID,
    event_type TEXT NOT NULL CHECK(event_type IN ('storefront','listing')),
    before_snapshot JSONB,
    after_snapshot JSONB NOT NULL,
    created_at_millis BIGINT NOT NULL
);
CREATE INDEX marketplace_publication_events_store_time ON marketplace_publication_events(store_id,created_at_millis DESC);
CREATE FUNCTION aita_market_publication_audit_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Publication audit events are immutable'; END $$;
CREATE TRIGGER marketplace_publication_audit_immutable BEFORE UPDATE OR DELETE ON marketplace_publication_events
FOR EACH ROW EXECUTE FUNCTION aita_market_publication_audit_immutable();
