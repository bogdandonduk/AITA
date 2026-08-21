-- AITA V47: stock sale/acceptance conditions and store-owned supplier management

ALTER TABLE stock_items
    ADD COLUMN IF NOT EXISTS conditions JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE stock_items
SET conditions = '[]'::jsonb
WHERE conditions IS NULL;

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS user_ids TEXT,
    ADD COLUMN IF NOT EXISTS category_ids TEXT,
    ADD COLUMN IF NOT EXISTS phone_numbers TEXT,
    ADD COLUMN IF NOT EXISTS emails TEXT,
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS stock_items_conditions_gin_idx
    ON stock_items USING GIN (conditions);

CREATE INDEX IF NOT EXISTS suppliers_is_active_idx
    ON suppliers (is_active);

CREATE INDEX IF NOT EXISTS suppliers_user_ids_idx
    ON suppliers (user_ids);

CREATE INDEX IF NOT EXISTS suppliers_phone_numbers_idx
    ON suppliers (phone_numbers);

CREATE INDEX IF NOT EXISTS suppliers_emails_idx
    ON suppliers (emails);
