-- Private item drafts and reviewed public listing snapshots are intentionally separate.
ALTER TABLE stock_items ADD COLUMN marketplace_profile JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE stock_items ADD CONSTRAINT stock_marketplace_profile_object
    CHECK (jsonb_typeof(marketplace_profile) = 'object');
UPDATE stock_items SET marketplace_profile = jsonb_build_object(
    'automaticFromStock', TRUE, 'name', name::jsonb, 'description', description::jsonb,
    'product', '{}'::jsonb);
ALTER TABLE marketplace_listings ADD COLUMN product JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE marketplace_listings ADD CONSTRAINT market_product_object CHECK (jsonb_typeof(product) = 'object');
ALTER TABLE marketplace_storefronts ADD COLUMN share_branch_availability BOOLEAN NOT NULL DEFAULT FALSE;

-- Retain previous branch drafts and audit history, but withdraw their public visibility.
UPDATE marketplace_listings l SET is_published=FALSE, revision=revision+1,
    updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
FROM stores s WHERE s.id=l.store_id AND s.parent_store_id IS NOT NULL AND l.is_published;
UPDATE marketplace_storefronts f SET is_published=FALSE, revision=revision+1,
    updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
FROM stores s WHERE s.id=f.store_id AND s.parent_store_id IS NOT NULL AND f.is_published;

CREATE FUNCTION aita_parent_marketplace_publication_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.is_published THEN
        -- Serialize publication with relocation/reparenting, not only a snapshot existence check.
        -- Normal application writes also lock this location before touching its publication rows.
        PERFORM id FROM stores WHERE id=NEW.store_id AND parent_store_id IS NULL FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Only a parent store may publish marketplace data' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER parent_storefront_publication_guard BEFORE INSERT OR UPDATE ON marketplace_storefronts
    FOR EACH ROW EXECUTE FUNCTION aita_parent_marketplace_publication_guard();
CREATE TRIGGER parent_listing_publication_guard BEFORE INSERT OR UPDATE ON marketplace_listings
    FOR EACH ROW EXECUTE FUNCTION aita_parent_marketplace_publication_guard();

-- Reparenting cannot turn a previously public parent into a separately published branch.
CREATE FUNCTION aita_withdraw_reparented_marketplace() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.parent_store_id IS NOT NULL AND NEW.parent_store_id IS DISTINCT FROM OLD.parent_store_id THEN
        UPDATE marketplace_listings SET is_published=FALSE, revision=revision+1,
            updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
            WHERE store_id=NEW.id AND is_published;
        UPDATE marketplace_storefronts SET is_published=FALSE, share_branch_availability=FALSE, revision=revision+1,
            updated_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint
            WHERE store_id=NEW.id AND (is_published OR share_branch_availability);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER withdraw_reparented_marketplace AFTER UPDATE OF parent_store_id ON stores
    FOR EACH ROW EXECUTE FUNCTION aita_withdraw_reparented_marketplace();
