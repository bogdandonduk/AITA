ALTER TABLE store_workers
  DROP COLUMN IF EXISTS store_sub_id CASCADE;

ALTER TABLE store_suppliers
  DROP COLUMN IF EXISTS store_sub_id CASCADE;