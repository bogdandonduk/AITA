-- A private checklist is buyer intent, separate from stock, orders and financial transactions.
ALTER TABLE buyer_shopping_lines ADD COLUMN collected BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE buyer_shopping_commands
    ADD COLUMN checklist_change JSONB,
    DROP CONSTRAINT buyer_shopping_basket_shape;
ALTER TABLE buyer_shopping_commands ADD CONSTRAINT buyer_shopping_command_shape CHECK (
    (basket_change IS NULL AND checklist_change IS NULL AND offer_id IS NOT NULL)
    OR (basket_change IS NOT NULL AND checklist_change IS NULL AND jsonb_typeof(basket_change)='object'
        AND basket_change ? 'lines' AND jsonb_typeof(basket_change->'lines')='array'
        AND jsonb_array_length(basket_change->'lines') BETWEEN 1 AND 50
        AND offer_id IS NULL AND requested_units=0 AND replaced_offer_id IS NULL
        AND reviewed_subtotal_minor IS NULL)
    OR (checklist_change IS NOT NULL AND basket_change IS NULL AND jsonb_typeof(checklist_change)='object'
        AND checklist_change ? 'offerIds' AND jsonb_typeof(checklist_change->'offerIds')='array'
        AND jsonb_array_length(checklist_change->'offerIds') BETWEEN 1 AND 50
        AND checklist_change ? 'action' AND jsonb_typeof(checklist_change->'action')='string'
        AND checklist_change->>'action' IN ('collect','uncheck','remove_collected')
        AND offer_id IS NULL AND requested_units=0 AND replaced_offer_id IS NULL
        AND reviewed_subtotal_minor IS NULL)
);
