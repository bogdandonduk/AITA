-- Buyer intent only: a reviewed, complete currency group is one immutable list command.
-- Existing pending command hashes, successful outcomes and original list rows remain untouched.
ALTER TABLE buyer_shopping_commands
    ALTER COLUMN offer_id DROP NOT NULL,
    ADD COLUMN basket_change JSONB,
    ADD CONSTRAINT buyer_shopping_basket_shape CHECK (
        (basket_change IS NULL AND offer_id IS NOT NULL)
        OR (basket_change IS NOT NULL AND jsonb_typeof(basket_change)='object'
            AND basket_change ? 'lines' AND jsonb_typeof(basket_change->'lines')='array'
            AND jsonb_array_length(basket_change->'lines') BETWEEN 1 AND 50
            AND offer_id IS NULL AND requested_units=0 AND replaced_offer_id IS NULL
            AND reviewed_subtotal_minor IS NULL)
    );
-- A rejected/no-op command must also advance the locked row's MVCC version. Otherwise a waiter
-- at REPEATABLE READ can miss that command even after acquiring an unchanged revision row.
-- This is NOT a shopping-list revision and does not manufacture a user-visible list edit.
ALTER TABLE buyer_shopping_lists ADD COLUMN last_command_id UUID;
