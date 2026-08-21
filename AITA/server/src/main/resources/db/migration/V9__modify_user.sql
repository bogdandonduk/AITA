CREATE EXTENSION IF NOT EXISTS citext;

ALTER TABLE users RENAME COLUMN store_worker_account_id TO worker_account_ids;
ALTER TABLE users RENAME COLUMN store_supplier_account_id TO supplier_account_ids;

ALTER TABLE users ALTER COLUMN worker_account_ids TYPE text USING worker_account_ids::text;
ALTER TABLE users ALTER COLUMN supplier_account_ids TYPE text USING supplier_account_ids::text;

ALTER TABLE users ALTER COLUMN phone_number TYPE varchar(32);
ALTER TABLE users ALTER COLUMN country_locale TYPE varchar(64);
