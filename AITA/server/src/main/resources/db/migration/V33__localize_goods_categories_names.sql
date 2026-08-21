-- Localize goods category names.
-- This migration is intentionally defensive: on newer databases the column is already JSONB,
-- while older prototypes may still have kept generic_goods_categories.name as plain text.
DO $$
DECLARE
  column_type text;
BEGIN
  SELECT data_type
  INTO column_type
  FROM information_schema.columns
  WHERE table_schema = 'public'
    AND table_name = 'generic_goods_categories'
    AND column_name = 'name';

  IF column_type IS NULL THEN
    ALTER TABLE generic_goods_categories
      ADD COLUMN name JSONB NOT NULL DEFAULT '[{"language":"main","value":""}]'::jsonb;
  ELSIF column_type IN ('text', 'character varying') THEN
    ALTER TABLE generic_goods_categories
      ALTER COLUMN name TYPE JSONB
      USING jsonb_build_array(
        jsonb_build_object('language', 'main', 'value', name::text),
        jsonb_build_object('language', 'en', 'value', name::text),
        jsonb_build_object('language', 'ru', 'value', name::text),
        jsonb_build_object('language', 'kk', 'value', name::text)
      );
  END IF;
END $$;