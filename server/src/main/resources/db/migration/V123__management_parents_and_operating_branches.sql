-- Preserve operating IDs: receipts, stock, queues and paid access remain at the same location.
-- Only a new management/warehouse parent is introduced above each former store family.
ALTER TABLE stores ADD COLUMN branch_type TEXT;
CREATE TABLE store_management_parent_migrations (
    original_store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE CASCADE,
    management_store_id UUID NOT NULL UNIQUE,
    migrated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO store_management_parent_migrations(original_store_id,management_store_id)
    SELECT id,gen_random_uuid() FROM stores WHERE parent_store_id IS NULL;

-- V124 replaces this with a branch-aware publication guard. Reparenting here must retain publication.
DROP TRIGGER IF EXISTS withdraw_reparented_marketplace ON stores;
INSERT INTO stores
    SELECT (jsonb_populate_record(NULL::stores, to_jsonb(s) || jsonb_build_object(
        'id',m.management_store_id,
        'public_id','S'||upper(substr(replace(m.management_store_id::text,'-',''),1,14)),
        'parent_store_id',NULL,'branch_type',NULL,'legal_id','','created_at',now(),'updated_at',now()
    ))).* FROM stores s JOIN store_management_parent_migrations m ON m.original_store_id=s.id;

UPDATE stores s SET parent_store_id=m.management_store_id,updated_at=now()
    FROM store_management_parent_migrations m WHERE s.parent_store_id=m.original_store_id;
UPDATE stores s SET parent_store_id=m.management_store_id,updated_at=now()
    FROM store_management_parent_migrations m WHERE s.id=m.original_store_id;
-- Transfer legal identity only after old roots have become branches; the V44 root-only
-- unique index remains installed throughout and never sees two parents with the same BIN/TIN.
UPDATE stores p SET legal_id=s.legal_id,legal_id_type_id=s.legal_id_type_id
    FROM store_management_parent_migrations m JOIN stores s ON s.id=m.original_store_id
    WHERE p.id=m.management_store_id;
UPDATE stores s SET branch_type=CASE WHEN EXISTS (
    SELECT 1 FROM marketplace_storefronts f WHERE f.store_id=s.id
) THEN 'INTERNET' ELSE 'PHYSICAL' END WHERE s.parent_store_id IS NOT NULL;
ALTER TABLE store_management_parent_migrations ADD CONSTRAINT management_parent_store_fk
    FOREIGN KEY(management_store_id) REFERENCES stores(id) ON DELETE CASCADE;
ALTER TABLE stores ADD CONSTRAINT store_branch_type_shape CHECK (
    (parent_store_id IS NULL AND branch_type IS NULL) OR
    (parent_store_id IS NOT NULL AND branch_type IS NOT NULL AND branch_type IN ('PHYSICAL','INTERNET'))
);

-- Root-wide employment/permissions stay root-wide; workshift and transaction location IDs stay intact.
INSERT INTO store_users(store_id,user_id)
    SELECT m.management_store_id,u.user_id FROM store_users u
    JOIN store_management_parent_migrations m ON m.original_store_id=u.store_id
    ON CONFLICT DO NOTHING;
UPDATE store_worker_memberships w SET store_id=m.management_store_id,updated_at=now()
    FROM store_management_parent_migrations m WHERE w.store_id=m.original_store_id;
UPDATE store_worker_requests w SET store_id=m.management_store_id,updated_at=now()
    FROM store_management_parent_migrations m WHERE w.store_id=m.original_store_id;
UPDATE store_worker_role_templates w SET store_id=m.management_store_id,updated_at=now()
    FROM store_management_parent_migrations m WHERE w.store_id=m.original_store_id;
UPDATE operation_logs l SET root_store_id=m.management_store_id
    FROM store_management_parent_migrations m WHERE l.root_store_id=m.original_store_id;
UPDATE stock_batch_movements l SET root_store_id=m.management_store_id
    FROM store_management_parent_migrations m WHERE l.root_store_id=m.original_store_id;

-- Current catalogue operations clone titles by location (branch pull, supplier receiving and
-- parent mirrors). Keep exactly one active barcode owner PER LOCATION, matching those handlers.
-- V58's old family-wide trigger prohibited these legitimate clones. Serialize barcode writers
-- within a location, while unrelated stores retain their independent barcode namespaces.
CREATE OR REPLACE FUNCTION aita_stock_items_no_duplicate_barcodes()
RETURNS trigger AS $$
DECLARE duplicated_barcode text;
BEGIN
    IF COALESCE(NEW.is_active,TRUE)=FALSE THEN RETURN NEW; END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('aita-stock-barcode:'||NEW.store_id::text,0));
    SELECT incoming.barcode INTO duplicated_barcode
    FROM jsonb_array_elements_text(COALESCE(NEW.barcodes,'[]'::jsonb)) AS incoming_raw(value)
    CROSS JOIN LATERAL (
        SELECT lower(regexp_replace(trim(incoming_raw.value),'\s+','','g')) AS barcode
    ) incoming
    WHERE incoming.barcode<>'' AND EXISTS (
        SELECT 1 FROM stock_items existing_item,
            jsonb_array_elements_text(COALESCE(existing_item.barcodes,'[]'::jsonb)) AS existing_raw(value)
        WHERE existing_item.store_id=NEW.store_id AND existing_item.is_active=TRUE
            AND existing_item.id<>NEW.id
            AND lower(regexp_replace(trim(existing_raw.value),'\s+','','g'))=incoming.barcode
    ) LIMIT 1;
    IF duplicated_barcode IS NOT NULL THEN
        RAISE EXCEPTION 'DUPLICATED_STOCK_BARCODE:%',duplicated_barcode USING ERRCODE='23505';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- No original item, batch quantity, shelf selection or receipt snapshot is rewritten.
-- New management parents receive catalogue titles only; they start with no physical batches.
INSERT INTO stock_items
    SELECT (jsonb_populate_record(NULL::stock_items,to_jsonb(i)||jsonb_build_object(
        'id',gen_random_uuid(),'store_id',m.management_store_id,'active_shelf_batch_id',NULL,
        'updated_at_millis',(extract(epoch FROM clock_timestamp())*1000)::bigint
    ))).* FROM stock_items i JOIN store_management_parent_migrations m ON m.original_store_id=i.store_id;

CREATE FUNCTION aita_operating_subscription_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM id FROM stores WHERE id=NEW.store_id AND parent_store_id IS NOT NULL FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subscriptions belong only to operating branches' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER operating_subscription_guard BEFORE INSERT OR UPDATE OF store_id ON store_subscription_states
    FOR EACH ROW EXECUTE FUNCTION aita_operating_subscription_guard();
CREATE TRIGGER operating_subscription_history_guard BEFORE INSERT OR UPDATE OF store_id ON store_subscriptions
    FOR EACH ROW EXECUTE FUNCTION aita_operating_subscription_guard();

CREATE FUNCTION aita_management_parent_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Management entities and operating entities cannot swap roles through an UPDATE.
    -- This also prevents leaving existing children nested or paid access attached to a parent.
    IF TG_OP='UPDATE' AND ((OLD.parent_store_id IS NULL) <> (NEW.parent_store_id IS NULL)) THEN
        RAISE EXCEPTION 'Management parents and operating branches have distinct identities' USING ERRCODE='23514';
    END IF;
    IF NEW.parent_store_id IS NOT NULL THEN
        PERFORM id FROM stores WHERE id=NEW.parent_store_id AND parent_store_id IS NULL AND id<>NEW.id FOR SHARE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'A branch must belong directly to a management parent' USING ERRCODE='23514';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER management_parent_guard BEFORE INSERT OR UPDATE OF parent_store_id ON stores
    FOR EACH ROW EXECUTE FUNCTION aita_management_parent_guard();
