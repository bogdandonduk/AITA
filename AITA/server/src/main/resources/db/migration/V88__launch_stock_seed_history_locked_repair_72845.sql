-- Forward-only repair after V82/V85 became history-locked in local Flyway databases.
-- Do not edit V82/V85 anymore; this migration replaces the retry function safely and runs one repair pass.

CREATE TABLE IF NOT EXISTS aita_launch_stock_seed_state (
    seed_key TEXT PRIMARY KEY,
    store_id UUID NULL,
    seeded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION aita_seed_launch_stock_for_target_parent_store()
RETURNS void AS $$
DECLARE
    target_store_id CONSTANT UUID := 'eddec888-51ba-4dd2-9de2-a9329d3b0493'::uuid;
    target_user_id CONSTANT UUID := '4c851e56-db00-4347-a596-06043a3d888f'::uuid;
    legacy_seed_key CONSTANT TEXT := 'first-store-toys-stock-v82';
    v85_repair_seed_key CONSTANT TEXT := 'first-store-toys-stock-v85-repair';
    v86_repair_seed_key CONSTANT TEXT := 'first-store-toys-stock-v86-history-locked-repair-72845';
    target_store_parent_id UUID;
    target_store_owner_user_ids JSONB;
    expected_seed_count INTEGER := 0;
    changed_count INTEGER := 0;
    ready_seed_count INTEGER := 0;
BEGIN
    IF to_regclass('public.aita_launch_stock_seed_items') IS NULL THEN
        DELETE FROM aita_launch_stock_seed_state
        WHERE seed_key = v86_repair_seed_key;

        RAISE NOTICE 'AITA launch stock V86 has no seed item table yet. Earlier seed migrations are history-locked, so V86 exits cleanly and waits for a later forward repair.';
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO expected_seed_count
    FROM aita_launch_stock_seed_items;

    IF expected_seed_count <= 0 THEN
        DELETE FROM aita_launch_stock_seed_state
        WHERE seed_key = v86_repair_seed_key;

        RAISE NOTICE 'AITA launch stock V86 has no seed item rows available; leaving repair unmarked so a later forward migration can retry safely.';
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM aita_launch_stock_seed_state WHERE seed_key = v86_repair_seed_key) THEN
        SELECT COUNT(*)
        INTO ready_seed_count
        FROM stock_items si
        JOIN aita_launch_stock_seed_items seed ON seed.stock_item_id = si.id
        WHERE si.store_id = target_store_id
          AND si.is_active = TRUE;

        IF ready_seed_count >= expected_seed_count THEN
            RETURN;
        END IF;

        DELETE FROM aita_launch_stock_seed_state
        WHERE seed_key = v86_repair_seed_key;

        RAISE NOTICE 'AITA launch stock V86 repair marker existed, but only % of % seed rows are active in target store %. Repair will retry.', ready_seed_count, expected_seed_count, target_store_id;
    END IF;

    SELECT
        s.parent_store_id,
        CASE
            WHEN jsonb_typeof(COALESCE(s.owner_user_ids, '[]'::jsonb)) IN ('array', 'object') THEN COALESCE(s.owner_user_ids, '[]'::jsonb)
            ELSE '[]'::jsonb
        END
    INTO target_store_parent_id, target_store_owner_user_ids
    FROM stores s
    WHERE s.id = target_store_id;

    IF NOT FOUND THEN
        RAISE NOTICE 'AITA launch stock V86 target store % does not exist yet. V86 will finish and retry automatically after the target store/user linkage is imported.', target_store_id;
        RETURN;
    END IF;

    IF target_store_parent_id IS NOT NULL THEN
        RAISE WARNING 'AITA launch stock V86 target store % is not a parent store; parent_store_id is %. V86 will retry if this store is corrected later.', target_store_id, target_store_parent_id;
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM users u
        WHERE u.id = target_user_id
          AND u.is_active = TRUE
    ) THEN
        RAISE NOTICE 'AITA launch stock V86 owner user % does not exist or is inactive yet. V86 will retry automatically after the target user is imported/activated.', target_user_id;
        RETURN;
    END IF;

    IF NOT (target_store_owner_user_ids ? target_user_id::TEXT)
       AND NOT EXISTS (
           SELECT 1
           FROM store_users su
           WHERE su.store_id = target_store_id
             AND su.user_id = target_user_id
       ) THEN
        RAISE NOTICE 'AITA launch stock V86 owner user % is not attached to target parent store % yet. V86 will retry automatically after stores.owner_user_ids or store_users is imported.', target_user_id, target_store_id;
        RETURN;
    END IF;

    WITH context AS (
        SELECT
            target_store_id AS store_id,
            target_user_id AS user_id,
            (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT AS now_millis
    ),
    target_store_group AS (
        SELECT COALESCE(s.parent_store_id, s.id, target_store_id) AS store_group_id
        FROM stores s
        WHERE s.id = target_store_id
    ),
    seed_clean AS (
        SELECT
            seed.source_row,
            seed.stock_item_id,
            seed.name,
            CASE
                WHEN jsonb_typeof(COALESCE(seed.barcodes, '[]'::jsonb)) = 'array' THEN COALESCE(seed.barcodes, '[]'::jsonb)
                ELSE '[]'::jsonb
            END AS barcodes,
            seed.sale_price
        FROM aita_launch_stock_seed_items seed
    ),
    existing_barcode_tokens AS (
        SELECT
            si.id AS stock_item_id,
            LOWER(regexp_replace(trim(existing_raw.value), '[^[:alnum:]]', '', 'g')) AS token
        FROM stock_items si
        LEFT JOIN stores existing_store ON existing_store.id = si.store_id
        CROSS JOIN target_store_group tsg
        CROSS JOIN LATERAL jsonb_array_elements_text(
            CASE
                WHEN jsonb_typeof(COALESCE(si.barcodes, '[]'::jsonb)) = 'array' THEN COALESCE(si.barcodes, '[]'::jsonb)
                ELSE '[]'::jsonb
            END
        ) AS existing_raw(value)
        WHERE si.is_active = TRUE
          AND COALESCE(existing_store.parent_store_id, existing_store.id, si.store_id) = tsg.store_group_id

        UNION

        SELECT
            si.id AS stock_item_id,
            LOWER(regexp_replace(trim(existing_model.value ->> 'value'), '[^[:alnum:]]', '', 'g')) AS token
        FROM stock_items si
        LEFT JOIN stores existing_store ON existing_store.id = si.store_id
        CROSS JOIN target_store_group tsg
        CROSS JOIN LATERAL jsonb_array_elements(
            CASE
                WHEN jsonb_typeof(COALESCE(si.barcode_models, '[]'::jsonb)) = 'array' THEN COALESCE(si.barcode_models, '[]'::jsonb)
                ELSE '[]'::jsonb
            END
        ) AS existing_model(value)
        WHERE si.is_active = TRUE
          AND COALESCE(existing_store.parent_store_id, existing_store.id, si.store_id) = tsg.store_group_id
    )
    INSERT INTO stock_items (
        id,
        user_id,
        store_id,
        barcodes,
        barcode_models,
        name,
        description,
        measurement_unit_id,
        category_ids,
        sale_prices,
        return_prices,
        supply_prices,
        wholesale_prices,
        wholesale_min_quantity,
        generic_expiration_period,
        is_quick_item,
        image_paths,
        active_shelf_batch_id,
        promotions,
        note,
        note_localized,
        conditions,
        created_at_millis,
        updated_at_millis,
        is_active
    )
    SELECT
        seed.stock_item_id,
        context.user_id,
        context.store_id,
        seed.barcodes,
        COALESCE((
            SELECT jsonb_agg(
                jsonb_build_object(
                    'value', barcode_row.barcode_value,
                    'type', 'internal',
                    'storeId', context.store_id::TEXT
                )
                ORDER BY barcode_row.ordinality
            )
            FROM jsonb_array_elements_text(seed.barcodes) WITH ORDINALITY AS barcode_row(barcode_value, ordinality)
        ), '[]'::jsonb),
        jsonb_build_array(
            jsonb_build_object('language', 'main', 'value', seed.name)
        ),
        '[]'::jsonb,
        '0',
        '["9f70a73c-13d9-51c7-8b3f-5b855d740bfb", "95be9187-eb55-548f-9aa0-fc8b668961d8"]'::jsonb,
        jsonb_build_array(jsonb_build_object('price', seed.sale_price, 'currency', 'KZT', 'supplierId', '')),
        jsonb_build_array(jsonb_build_object('price', seed.sale_price, 'currency', 'KZT', 'supplierId', '')),
        '[]'::jsonb,
        '[]'::jsonb,
        NULL,
        NULL,
        FALSE,
        '[]'::jsonb,
        NULL,
        '[]'::jsonb,
        NULL,
        '[]'::jsonb,
        '[]'::jsonb,
        context.now_millis,
        context.now_millis,
        TRUE
    FROM seed_clean seed
    CROSS JOIN context
    WHERE NOT EXISTS (
        SELECT 1
        FROM jsonb_array_elements_text(seed.barcodes) AS incoming_raw(value)
        CROSS JOIN LATERAL (
            SELECT LOWER(regexp_replace(trim(incoming_raw.value), '[^[:alnum:]]', '', 'g')) AS token
        ) incoming
        WHERE incoming.token <> ''
          AND EXISTS (
              SELECT 1
              FROM existing_barcode_tokens existing
              WHERE existing.token = incoming.token
                AND existing.stock_item_id <> seed.stock_item_id
          )
    )
    ON CONFLICT (id) DO UPDATE SET
        user_id = EXCLUDED.user_id,
        store_id = EXCLUDED.store_id,
        barcodes = EXCLUDED.barcodes,
        barcode_models = EXCLUDED.barcode_models,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        measurement_unit_id = EXCLUDED.measurement_unit_id,
        category_ids = EXCLUDED.category_ids,
        sale_prices = EXCLUDED.sale_prices,
        return_prices = EXCLUDED.return_prices,
        supply_prices = EXCLUDED.supply_prices,
        wholesale_prices = EXCLUDED.wholesale_prices,
        wholesale_min_quantity = EXCLUDED.wholesale_min_quantity,
        generic_expiration_period = EXCLUDED.generic_expiration_period,
        is_quick_item = EXCLUDED.is_quick_item,
        image_paths = EXCLUDED.image_paths,
        promotions = EXCLUDED.promotions,
        note = EXCLUDED.note,
        note_localized = EXCLUDED.note_localized,
        conditions = EXCLUDED.conditions,
        updated_at_millis = EXCLUDED.updated_at_millis,
        is_active = EXCLUDED.is_active
    WHERE stock_items.store_id = target_store_id;

    GET DIAGNOSTICS changed_count = ROW_COUNT;

    SELECT COUNT(*)
    INTO ready_seed_count
    FROM stock_items si
    JOIN aita_launch_stock_seed_items seed ON seed.stock_item_id = si.id
    WHERE si.store_id = target_store_id
      AND si.is_active = TRUE;

    IF ready_seed_count >= expected_seed_count THEN
        INSERT INTO aita_launch_stock_seed_state(seed_key, store_id, seeded_at)
        VALUES(legacy_seed_key, target_store_id, CURRENT_TIMESTAMP)
        ON CONFLICT (seed_key) DO UPDATE SET
            store_id = EXCLUDED.store_id,
            seeded_at = EXCLUDED.seeded_at;

        INSERT INTO aita_launch_stock_seed_state(seed_key, store_id, seeded_at)
        VALUES(v85_repair_seed_key, target_store_id, CURRENT_TIMESTAMP)
        ON CONFLICT (seed_key) DO UPDATE SET
            store_id = EXCLUDED.store_id,
            seeded_at = EXCLUDED.seeded_at;

        INSERT INTO aita_launch_stock_seed_state(seed_key, store_id, seeded_at)
        VALUES(v86_repair_seed_key, target_store_id, CURRENT_TIMESTAMP)
        ON CONFLICT (seed_key) DO UPDATE SET
            store_id = EXCLUDED.store_id,
            seeded_at = EXCLUDED.seeded_at;

        RAISE NOTICE 'AITA launch stock V86 repaired/verified % of % stock item rows in parent store % for owner user %; changed rows in this pass: %.', ready_seed_count, expected_seed_count, target_store_id, target_user_id, changed_count;
    ELSE
        DELETE FROM aita_launch_stock_seed_state
        WHERE seed_key = v86_repair_seed_key;

        RAISE WARNING 'AITA launch stock V86 could verify only % of % seed rows in parent store %. This usually means some seed barcodes are already owned by other active stock cards; V86 is left retryable and does not fail Flyway.', ready_seed_count, expected_seed_count, target_store_id;
    END IF;
EXCEPTION WHEN OTHERS THEN
    DELETE FROM aita_launch_stock_seed_state
    WHERE seed_key = v86_repair_seed_key;

    RAISE WARNING 'AITA launch stock V86 repair seed skipped instead of failing Flyway. SQLSTATE %, message: %', SQLSTATE, SQLERRM;
    RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION aita_try_seed_launch_stock_for_target_parent_store()
RETURNS trigger AS $$
BEGIN
    PERFORM aita_seed_launch_stock_for_target_parent_store();
    RETURN NEW;
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'AITA launch stock V86 retry skipped after trigger because seeding failed: %', SQLERRM;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_aita_seed_launch_stock_after_store_insert ON stores;
DROP TRIGGER IF EXISTS trg_aita_seed_launch_stock_after_target_store_change ON stores;
DROP TRIGGER IF EXISTS trg_aita_seed_launch_stock_after_target_user_change ON users;
DROP TRIGGER IF EXISTS trg_aita_seed_launch_stock_after_target_store_user_change ON store_users;

CREATE TRIGGER trg_aita_seed_launch_stock_after_target_store_change
AFTER INSERT OR UPDATE OF parent_store_id, owner_user_ids ON stores
FOR EACH ROW
WHEN (NEW.id = '3a4686ae-8d3e-45ac-affc-21ca516a0912'::uuid)
EXECUTE FUNCTION aita_try_seed_launch_stock_for_target_parent_store();

CREATE TRIGGER trg_aita_seed_launch_stock_after_target_user_change
AFTER INSERT OR UPDATE OF is_active ON users
FOR EACH ROW
WHEN (NEW.id = 'ceb44c8c-b11f-44c0-acb1-f93f4b1490f9'::uuid)
EXECUTE FUNCTION aita_try_seed_launch_stock_for_target_parent_store();

CREATE TRIGGER trg_aita_seed_launch_stock_after_target_store_user_change
AFTER INSERT OR UPDATE ON store_users
FOR EACH ROW
WHEN (
    NEW.store_id = 'eddec888-51ba-4dd2-9de2-a9329d3b0493'::uuid
    AND NEW.user_id = 'ceb44c8c-b11f-44c0-acb1-f93f4b1490f9'::uuid
)
EXECUTE FUNCTION aita_try_seed_launch_stock_for_target_parent_store();

SELECT aita_seed_launch_stock_for_target_parent_store();
