-- Buyer planning only: record the reviewed replacement alongside its immutable command outcome.
-- Legacy rows are untouched; their JSON request hashes and idempotent retries remain valid.
ALTER TABLE buyer_shopping_commands
    ADD COLUMN replaced_offer_id UUID,
    ADD COLUMN reviewed_subtotal_minor BIGINT,
    ADD CONSTRAINT buyer_shopping_replacement_shape CHECK (
        (replaced_offer_id IS NULL AND reviewed_subtotal_minor IS NULL)
        OR (replaced_offer_id IS NOT NULL AND replaced_offer_id<>offer_id AND requested_units>0
            AND reviewed_subtotal_minor IS NOT NULL AND reviewed_subtotal_minor>=0)
    );
-- Same-product queries reuse marketplace_listings_gtin from V104; do not duplicate that index.
