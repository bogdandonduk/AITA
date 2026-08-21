ALTER TABLE generic_goods_categories
  ALTER COLUMN type_ids TYPE jsonb USING type_ids::jsonb,
  ALTER COLUMN type_ids SET DEFAULT '[]'::jsonb,
  ALTER COLUMN image_paths TYPE jsonb USING image_paths::jsonb,
  ALTER COLUMN image_paths SET DEFAULT '[]'::jsonb;

