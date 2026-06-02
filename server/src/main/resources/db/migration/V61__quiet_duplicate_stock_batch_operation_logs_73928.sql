DELETE FROM operation_logs
WHERE entity_id IS NULL
  AND lower(coalesce(entity_type, '')) IN ('stock_batch', 'stockbatch', 'stock batches', 'stock_batch_v2')
  AND (
        lower(coalesce(metadata ->> 'http_path', '')) LIKE 'stockbatches/setactiveshelfbatch%'
        OR lower(coalesce(metadata ->> 'http_path', '')) LIKE 'stockbatches/add%'
        OR lower(coalesce(metadata ->> 'http_path', '')) LIKE 'stockbatches/update%'
        OR lower(title::text) LIKE '%stock batch saved%'
      );

WITH ranked AS (
    SELECT
        id,
        row_number() OVER (
            PARTITION BY
                root_store_id,
                store_id,
                actor_user_id,
                action,
                entity_type,
                coalesce(entity_id, ''),
                title::text,
                details::text,
                metadata::text,
                floor(created_at_millis / 10000)
            ORDER BY created_at_millis ASC, id ASC
        ) AS rn
    FROM operation_logs
)
DELETE FROM operation_logs logs
USING ranked
WHERE logs.id = ranked.id
  AND ranked.rn > 1;
