DO $$
BEGIN
  -- Old early schema had stores.user_id uuid. Later code/migrations expected stores.user_ids json/jsonb.
  -- This migration is defensive so a fresh database can pass whether it has user_id, user_ids, or neither.

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'stores'
      AND column_name = 'user_ids'
  ) THEN
    EXECUTE 'ALTER TABLE stores ADD COLUMN user_ids jsonb NOT NULL DEFAULT ''[]''::jsonb';

    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_name = 'stores'
        AND column_name = 'user_id'
    ) THEN
      EXECUTE 'UPDATE stores SET user_ids = jsonb_build_array(user_id::text) WHERE user_id IS NOT NULL';
    END IF;
  ELSE
    EXECUTE '
      ALTER TABLE stores
        ALTER COLUMN user_ids TYPE jsonb
        USING CASE
          WHEN user_ids IS NULL THEN ''[]''::jsonb
          WHEN user_ids::text = '''' THEN ''[]''::jsonb
          ELSE user_ids::jsonb
        END
    ';

    EXECUTE 'ALTER TABLE stores ALTER COLUMN user_ids SET DEFAULT ''[]''::jsonb';
  END IF;
END
$$;
