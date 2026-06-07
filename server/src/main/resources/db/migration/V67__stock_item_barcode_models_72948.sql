ALTER TABLE stock_items
    ADD COLUMN IF NOT EXISTS barcode_models JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE stock_items si
SET barcode_models = backfilled.models
FROM (
    SELECT
        source.id,
        COALESCE(
            jsonb_agg(
                jsonb_strip_nulls(
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
) AS backfilled
WHERE si.id = backfilled.id
  AND (si.barcode_models IS NULL OR si.barcode_models = '[]'::jsonb);

CREATE INDEX IF NOT EXISTS idx_stock_items_barcode_models_gin
    ON stock_items USING GIN (barcode_models);
