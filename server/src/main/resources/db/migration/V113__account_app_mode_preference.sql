-- Add without a default first: existing accounts stay NULL and preserve their owned local choice.
ALTER TABLE users ADD COLUMN app_mode_id INTEGER;
-- Only future inserts receive Buyer; never switch existing business accounts during migration.
ALTER TABLE users ALTER COLUMN app_mode_id SET DEFAULT 1;
ALTER TABLE users ADD CONSTRAINT users_app_mode_id_valid CHECK (app_mode_id IS NULL OR app_mode_id IN (0, 1, 2));
