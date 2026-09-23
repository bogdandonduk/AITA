ALTER TABLE auth_one_time_challenges ADD COLUMN IF NOT EXISTS replaced_by_public_id UUID;
