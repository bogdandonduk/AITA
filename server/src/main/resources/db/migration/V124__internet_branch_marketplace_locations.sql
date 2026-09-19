-- V123 has already made operating stores branches. Remove the retired parent-only guards
-- before updating any existing published storefront, or their backfill would be rejected.
DROP TRIGGER IF EXISTS parent_storefront_publication_guard ON marketplace_storefronts;
DROP TRIGGER IF EXISTS parent_listing_publication_guard ON marketplace_listings;
DROP FUNCTION IF EXISTS aita_parent_marketplace_publication_guard();

-- A public shop keeps its identity/bookmarks while its operating branch owns access and stock.
ALTER TABLE marketplace_storefronts DROP CONSTRAINT marketplace_storefronts_store_id_fkey;
ALTER TABLE marketplace_storefronts ADD COLUMN branch_store_id UUID REFERENCES stores(id) ON DELETE CASCADE;
UPDATE marketplace_storefronts SET branch_store_id=store_id;
ALTER TABLE marketplace_storefronts ALTER COLUMN branch_store_id SET NOT NULL;
ALTER TABLE marketplace_storefronts ADD CONSTRAINT marketplace_storefronts_branch_unique UNIQUE(branch_store_id);

-- Bookmarks refer to the stable public shop, which need not have the operating store's UUID.
ALTER TABLE buyer_saved_shops DROP CONSTRAINT buyer_saved_shops_store_id_fkey;
ALTER TABLE buyer_saved_shops ADD CONSTRAINT buyer_saved_shops_public_storefront_fkey
    FOREIGN KEY(store_id) REFERENCES marketplace_storefronts(store_id) ON DELETE CASCADE NOT VALID;
-- Keep historical unavailable bookmarks if an old storefront was removed independently.
-- New writes are checked even when those preserved rows prevent full validation.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM buyer_saved_shops b LEFT JOIN marketplace_storefronts f
        ON f.store_id=b.store_id WHERE f.store_id IS NULL) THEN
        ALTER TABLE buyer_saved_shops VALIDATE CONSTRAINT buyer_saved_shops_public_storefront_fkey;
    END IF;
END $$;
ALTER TABLE marketplace_storefronts ADD COLUMN location_store_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE marketplace_storefronts ADD CONSTRAINT marketplace_location_array CHECK (
    jsonb_typeof(location_store_ids)='array' AND jsonb_array_length(location_store_ids)<=12);

-- Preserve the old explicit sharing consent as a fixed set. Future branches are never added silently.
UPDATE marketplace_storefronts f SET location_store_ids=coalesce((
    SELECT jsonb_agg(chosen.id::text ORDER BY chosen.id) FROM (
        SELECT b.id FROM stores b JOIN stores owner ON owner.id=f.branch_store_id
        WHERE b.parent_store_id=owner.parent_store_id AND b.branch_type='PHYSICAL'
            AND b.is_active AND length(trim(coalesce(b.address,'')))>0
        ORDER BY b.id LIMIT 12
    ) chosen
),'[]'::jsonb) WHERE f.share_branch_availability;

DROP TRIGGER IF EXISTS withdraw_reparented_marketplace ON stores;
DROP FUNCTION IF EXISTS aita_withdraw_reparented_marketplace();

CREATE FUNCTION aita_internet_marketplace_publication_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE branch_id UUID;
BEGIN
    IF TG_TABLE_NAME='marketplace_storefronts' THEN
        branch_id:=NEW.branch_store_id;
    ELSE
        SELECT branch_store_id INTO branch_id FROM marketplace_storefronts WHERE store_id=NEW.store_id;
    END IF;
    IF NEW.is_published THEN
        -- Serialize against a type, family or active-state change of the operating branch.
        PERFORM s.id FROM stores s JOIN stores p ON p.id=s.parent_store_id
            WHERE s.id=branch_id AND s.is_active AND s.branch_type='INTERNET'
                AND p.is_active AND p.parent_store_id IS NULL FOR SHARE OF s,p;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Only an active internet branch may publish marketplace data' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER internet_storefront_publication_guard BEFORE INSERT OR UPDATE ON marketplace_storefronts
    FOR EACH ROW EXECUTE FUNCTION aita_internet_marketplace_publication_guard();
CREATE TRIGGER internet_listing_publication_guard BEFORE INSERT OR UPDATE ON marketplace_listings
    FOR EACH ROW EXECUTE FUNCTION aita_internet_marketplace_publication_guard();

CREATE FUNCTION aita_withdraw_changed_marketplace_branch() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.parent_store_id IS DISTINCT FROM OLD.parent_store_id
        OR NEW.branch_type IS DISTINCT FROM OLD.branch_type
        OR (OLD.is_active AND NOT NEW.is_active) THEN
        UPDATE marketplace_listings l SET is_published=FALSE,revision=l.revision+1,
            updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
            FROM marketplace_storefronts f WHERE f.branch_store_id=NEW.id AND l.store_id=f.store_id AND l.is_published;
        UPDATE marketplace_storefronts SET is_published=FALSE,share_branch_availability=FALSE,
            location_store_ids='[]'::jsonb,revision=revision+1,
            updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
            WHERE branch_store_id=NEW.id AND (is_published OR share_branch_availability OR location_store_ids<>'[]'::jsonb);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER withdraw_changed_marketplace_branch AFTER UPDATE OF parent_store_id,branch_type,is_active ON stores
    FOR EACH ROW EXECUTE FUNCTION aita_withdraw_changed_marketplace_branch();
