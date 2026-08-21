CREATE TEMP TABLE aita_duplicate_auto_goods_category_ids (
    old_id text PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO generic_goods_categories (
    id,
    type_ids,
    name,
    alias,
    description,
    quantity_unit_id,
    image_paths
)
VALUES (
    '663afe3c-a740-3690-bf26-6ea6c4de06a0'::uuid,
    '[]'::jsonb,
    '[{"language":"main","value":"Auto goods"},{"language":"en","value":"Auto goods"},{"language":"ru","value":"Автотовары"},{"language":"kk","value":"Автотауарлар"}]'::jsonb,
    NULL,
    NULL,
    '0',
    '[]'::jsonb
)
ON CONFLICT (id) DO UPDATE
SET
    type_ids = EXCLUDED.type_ids,
    name = EXCLUDED.name,
    alias = EXCLUDED.alias,
    description = EXCLUDED.description,
    quantity_unit_id = EXCLUDED.quantity_unit_id,
    image_paths = EXCLUDED.image_paths;

INSERT INTO aita_duplicate_auto_goods_category_ids (old_id)
SELECT id::text
FROM generic_goods_categories
WHERE id <> '663afe3c-a740-3690-bf26-6ea6c4de06a0'::uuid
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(name) AS localized_name
      WHERE lower(trim(localized_name ->> 'value')) IN ('auto goods', 'автотовары', 'автотауарлар')
  )
ON CONFLICT DO NOTHING;

UPDATE stock_items AS stock
SET category_ids = (
    SELECT COALESCE(jsonb_agg(to_jsonb(category_id) ORDER BY first_ordinal), '[]'::jsonb)
    FROM (
        SELECT
            CASE
                WHEN existing_category.value IN (SELECT old_id FROM aita_duplicate_auto_goods_category_ids)
                    THEN '663afe3c-a740-3690-bf26-6ea6c4de06a0'
                ELSE existing_category.value
            END AS category_id,
            MIN(existing_category.ordinal) AS first_ordinal
        FROM jsonb_array_elements_text(stock.category_ids) WITH ORDINALITY AS existing_category(value, ordinal)
        GROUP BY 1
    ) AS normalized_categories
)
WHERE EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(stock.category_ids) AS existing_category(value)
    WHERE existing_category.value IN (SELECT old_id FROM aita_duplicate_auto_goods_category_ids)
);

UPDATE generic_goods_categories AS category
SET type_ids = (
    SELECT COALESCE(jsonb_agg(to_jsonb(category_id) ORDER BY first_ordinal), '[]'::jsonb)
    FROM (
        SELECT
            CASE
                WHEN existing_category.value IN (SELECT old_id FROM aita_duplicate_auto_goods_category_ids)
                    THEN '663afe3c-a740-3690-bf26-6ea6c4de06a0'
                ELSE existing_category.value
            END AS category_id,
            MIN(existing_category.ordinal) AS first_ordinal
        FROM jsonb_array_elements_text(category.type_ids) WITH ORDINALITY AS existing_category(value, ordinal)
        GROUP BY 1
    ) AS normalized_categories
)
WHERE EXISTS (
    SELECT 1
    FROM jsonb_array_elements_text(category.type_ids) AS existing_category(value)
    WHERE existing_category.value IN (SELECT old_id FROM aita_duplicate_auto_goods_category_ids)
);

DO $$
DECLARE
    duplicate_category_id text;
    canonical_category_id text := '663afe3c-a740-3690-bf26-6ea6c4de06a0';
BEGIN
    FOR duplicate_category_id IN SELECT old_id FROM aita_duplicate_auto_goods_category_ids LOOP
        UPDATE generic_goods_items
        SET category_ids = replace(category_ids, duplicate_category_id, canonical_category_id)
        WHERE category_ids IS NOT NULL
          AND category_ids LIKE '%' || duplicate_category_id || '%';

        UPDATE suppliers
        SET category_ids = replace(category_ids, duplicate_category_id, canonical_category_id)
        WHERE category_ids IS NOT NULL
          AND category_ids LIKE '%' || duplicate_category_id || '%';

        UPDATE manufacturers
        SET category_ids = replace(category_ids, duplicate_category_id, canonical_category_id)
        WHERE category_ids IS NOT NULL
          AND category_ids LIKE '%' || duplicate_category_id || '%';
    END LOOP;
END $$;

DELETE FROM generic_goods_categories
WHERE id::text IN (SELECT old_id FROM aita_duplicate_auto_goods_category_ids);

DROP TABLE aita_duplicate_auto_goods_category_ids;
