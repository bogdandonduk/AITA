ALTER TABLE stock_items
    ADD COLUMN IF NOT EXISTS barcode_models JSONB;

UPDATE stock_items
SET barcode_models = '[]'::jsonb
WHERE barcode_models IS NULL
   OR jsonb_typeof(barcode_models) <> 'array';

ALTER TABLE stock_items
    ALTER COLUMN barcode_models SET DEFAULT '[]'::jsonb;

WITH legacy_barcodes AS (
    SELECT
        source.id,
        COALESCE(
            jsonb_agg(
                DISTINCT jsonb_strip_nulls(
                    jsonb_build_object(
                        'value', trim(barcode_value),
                        'type', CASE
                            WHEN trim(barcode_value) ~ '^[0-9]{8}$|^[0-9]{12}$|^[0-9]{13}$|^[0-9]{14}$' THEN 'standard'
                            ELSE 'internal'
                        END,
                        'storeId', CASE
                            WHEN trim(barcode_value) ~ '^[0-9]{8}$|^[0-9]{12}$|^[0-9]{13}$|^[0-9]{14}$' THEN NULL
                            ELSE source.store_id::text
                        END
                    )
                )
            ) FILTER (WHERE trim(barcode_value) <> ''),
            '[]'::jsonb
        ) AS models
    FROM stock_items source
    LEFT JOIN LATERAL jsonb_array_elements_text(
        CASE
            WHEN jsonb_typeof(source.barcodes) = 'array' THEN source.barcodes
            ELSE '[]'::jsonb
        END
    ) AS barcode_values(barcode_value) ON TRUE
    GROUP BY source.id
)
UPDATE stock_items si
SET barcode_models = legacy_barcodes.models
FROM legacy_barcodes
WHERE si.id = legacy_barcodes.id
  AND si.barcode_models = '[]'::jsonb
  AND legacy_barcodes.models <> '[]'::jsonb;

WITH exploded AS (
    SELECT
        si.id,
        si.store_id,
        CASE
            WHEN jsonb_typeof(model) = 'object' THEN trim(COALESCE(model ->> 'value', ''))
            ELSE trim(both '"' FROM model::text)
        END AS value,
        CASE
            WHEN lower(trim(COALESCE(model ->> 'type', ''))) = 'internal' THEN 'internal'
            WHEN lower(trim(COALESCE(model ->> 'type', ''))) = 'standard' THEN 'standard'
            WHEN (CASE WHEN jsonb_typeof(model) = 'object' THEN trim(COALESCE(model ->> 'value', '')) ELSE trim(both '"' FROM model::text) END) ~ '^[0-9]{8}$|^[0-9]{12}$|^[0-9]{13}$|^[0-9]{14}$' THEN 'standard'
            ELSE 'internal'
        END AS barcode_type,
        NULLIF(trim(COALESCE(model ->> 'storeId', '')), '') AS explicit_store_id
    FROM stock_items si
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE
            WHEN jsonb_typeof(si.barcode_models) = 'array' THEN si.barcode_models
            ELSE '[]'::jsonb
        END
    ) AS model
), normalized AS (
    SELECT
        id,
        COALESCE(
            jsonb_agg(
                DISTINCT jsonb_strip_nulls(
                    jsonb_build_object(
                        'value', value,
                        'type', barcode_type,
                        'storeId', CASE
                            WHEN barcode_type = 'internal' THEN COALESCE(explicit_store_id, store_id::text)
                            ELSE NULL
                        END
                    )
                )
            ) FILTER (WHERE value <> ''),
            '[]'::jsonb
        ) AS models
    FROM exploded
    GROUP BY id
)
UPDATE stock_items si
SET barcode_models = normalized.models
FROM normalized
WHERE si.id = normalized.id;

UPDATE stock_items
SET barcode_models = '[]'::jsonb
WHERE barcode_models IS NULL
   OR jsonb_typeof(barcode_models) <> 'array';

ALTER TABLE stock_items
    ALTER COLUMN barcode_models SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_stock_items_barcode_models_gin
    ON stock_items USING GIN (barcode_models);
