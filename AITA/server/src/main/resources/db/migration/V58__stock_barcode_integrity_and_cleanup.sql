-- Clean rows left by old soft-delete behavior before enforcing barcode integrity.
DELETE FROM stock_batches
WHERE goods_item_id IN (
    SELECT id FROM stock_items WHERE is_active = FALSE
)
   OR is_active = FALSE
   OR lower(status) = 'deleted';

DELETE FROM stock_items
WHERE is_active = FALSE;

-- Normalize each active item to unique non-empty barcode values inside its own JSON array.
UPDATE stock_items si
SET barcodes = COALESCE(normalized.barcodes, '[]'::jsonb)
FROM (
    SELECT id,
           jsonb_agg(barcode ORDER BY first_position) AS barcodes
    FROM (
        SELECT id,
               MIN(barcode) AS barcode,
               MIN(position) AS first_position
        FROM (
            SELECT stock_items.id,
                   regexp_replace(trim(value), '\s+', '', 'g') AS barcode,
                   ordinality AS position
            FROM stock_items,
                 jsonb_array_elements_text(COALESCE(stock_items.barcodes, '[]'::jsonb)) WITH ORDINALITY AS value_rows(value, ordinality)
            WHERE stock_items.is_active = TRUE
        ) raw_barcodes
        WHERE barcode <> ''
        GROUP BY id, lower(barcode)
    ) unique_barcodes
    GROUP BY id
) normalized
WHERE si.id = normalized.id;

-- If old data already contains the same active barcode in two stock cards in the same store/branch group,
-- keep the oldest occurrence and remove that barcode from the later cards before installing the guard trigger.
WITH expanded AS (
    SELECT si.id AS item_id,
           lower(regexp_replace(trim(barcode_rows.value), '\s+', '', 'g')) AS barcode,
           COALESCE(s.parent_store_id, s.id, si.store_id) AS store_group_id,
           si.created_at_millis,
           si.updated_at_millis,
           barcode_rows.ordinality
    FROM stock_items si
    LEFT JOIN stores s ON s.id = si.store_id
    CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(si.barcodes, '[]'::jsonb)) WITH ORDINALITY AS barcode_rows(value, ordinality)
    WHERE si.is_active = TRUE
), duplicate_barcodes AS (
    SELECT item_id, barcode
    FROM (
        SELECT expanded.*,
               row_number() OVER (
                   PARTITION BY store_group_id, barcode
                   ORDER BY created_at_millis NULLS LAST, updated_at_millis NULLS LAST, item_id
               ) AS duplicate_rank
        FROM expanded
        WHERE barcode <> ''
    ) ranked
    WHERE duplicate_rank > 1
), normalized_after_duplicate_cleanup AS (
    SELECT si.id,
           COALESCE(
               jsonb_agg(cleaned.value ORDER BY cleaned.ordinality) FILTER (WHERE cleaned.value IS NOT NULL),
               '[]'::jsonb
           ) AS barcodes
    FROM stock_items si
    LEFT JOIN LATERAL (
        SELECT barcode_rows.value,
               barcode_rows.ordinality,
               lower(regexp_replace(trim(barcode_rows.value), '\s+', '', 'g')) AS normalized_value
        FROM jsonb_array_elements_text(COALESCE(si.barcodes, '[]'::jsonb)) WITH ORDINALITY AS barcode_rows(value, ordinality)
    ) cleaned ON NOT EXISTS (
        SELECT 1
        FROM duplicate_barcodes duplicate
        WHERE duplicate.item_id = si.id
          AND duplicate.barcode = cleaned.normalized_value
    )
    WHERE si.id IN (SELECT item_id FROM duplicate_barcodes)
    GROUP BY si.id
)
UPDATE stock_items si
SET barcodes = normalized_after_duplicate_cleanup.barcodes
FROM normalized_after_duplicate_cleanup
WHERE si.id = normalized_after_duplicate_cleanup.id;

CREATE OR REPLACE FUNCTION aita_stock_items_no_duplicate_barcodes()
RETURNS trigger AS $$
DECLARE
    duplicated_barcode text;
    new_store_group_id uuid;
BEGIN
    IF COALESCE(NEW.is_active, TRUE) = FALSE THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(stores.parent_store_id, stores.id, NEW.store_id)
    INTO new_store_group_id
    FROM stores
    WHERE stores.id = NEW.store_id;

    new_store_group_id := COALESCE(new_store_group_id, NEW.store_id);

    SELECT incoming.barcode
    INTO duplicated_barcode
    FROM jsonb_array_elements_text(COALESCE(NEW.barcodes, '[]'::jsonb)) AS incoming_raw(value)
    CROSS JOIN LATERAL (
        SELECT lower(regexp_replace(trim(incoming_raw.value), '\s+', '', 'g')) AS barcode
    ) incoming
    WHERE incoming.barcode <> ''
      AND EXISTS (
        SELECT 1
        FROM stock_items existing_item
        LEFT JOIN stores existing_store ON existing_store.id = existing_item.store_id,
             jsonb_array_elements_text(COALESCE(existing_item.barcodes, '[]'::jsonb)) AS existing_raw(value)
        WHERE COALESCE(existing_store.parent_store_id, existing_store.id, existing_item.store_id) = new_store_group_id
          AND existing_item.is_active = TRUE
          AND existing_item.id <> NEW.id
          AND lower(regexp_replace(trim(existing_raw.value), '\s+', '', 'g')) = incoming.barcode
      )
    LIMIT 1;

    IF duplicated_barcode IS NOT NULL THEN
        RAISE EXCEPTION 'DUPLICATED_STOCK_BARCODE:%', duplicated_barcode USING ERRCODE = '23505';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_stock_items_no_duplicate_barcodes ON stock_items;

CREATE TRIGGER trg_stock_items_no_duplicate_barcodes
BEFORE INSERT OR UPDATE OF store_id, barcodes, is_active ON stock_items
FOR EACH ROW
EXECUTE FUNCTION aita_stock_items_no_duplicate_barcodes();
