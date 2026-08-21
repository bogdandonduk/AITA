ALTER TABLE generic_goods_categories
    ADD COLUMN IF NOT EXISTS conditions JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE generic_goods_categories
SET conditions = $conditions$[
  "aita-stock-condition-v1:{\"kind\":\"buyer_minimum_age\",\"transactionTypeIndex\":0,\"text\":[],\"minimumAge\":21,\"startsAtMinutes\":360,\"endsAtMinutes\":1320}",
  "aita-stock-condition-v1:{\"kind\":\"transaction_time_window\",\"transactionTypeIndex\":0,\"text\":[],\"minimumAge\":18,\"startsAtMinutes\":360,\"endsAtMinutes\":1320}"
]$conditions$::jsonb
WHERE conditions = '[]'::jsonb
  AND (
      name::text ILIKE '%alcohol%'
      OR name::text ILIKE '%beer%'
      OR name::text ILIKE '%wine%'
      OR name::text ILIKE '%vodka%'
      OR name::text ILIKE '%spirit%'
      OR name::text ILIKE '%алког%'
      OR name::text ILIKE '%пиво%'
      OR name::text ILIKE '%вино%'
      OR name::text ILIKE '%водк%'
      OR name::text ILIKE '%спирт%'
      OR name::text ILIKE '%шарап%'
      OR name::text ILIKE '%сыра%'
  );
