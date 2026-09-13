-- Supports the bounded public-shop window; no shop is published by this migration.
CREATE INDEX marketplace_storefronts_public_name_order
    ON marketplace_storefronts ((lower(display_name) COLLATE "C"), store_id)
    WHERE is_published;
