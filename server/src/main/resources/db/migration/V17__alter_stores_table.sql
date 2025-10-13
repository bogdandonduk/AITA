ALTER TABLE stores
  ALTER COLUMN user_ids TYPE jsonb USING user_ids::jsonb,
  ALTER COLUMN user_ids SET DEFAULT '[]'::jsonb;

