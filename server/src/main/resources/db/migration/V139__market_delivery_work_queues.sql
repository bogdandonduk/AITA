-- Completed history must not crowd active operational work out of the first page.
-- Keyset order is unchanged, including equal creation timestamps.
CREATE INDEX market_delivery_buyer_active_page
    ON market_delivery_orders(buyer_id,created_at_millis DESC,id DESC)
    WHERE status NOT IN ('DELIVERED','CANCELLED');
CREATE INDEX market_delivery_seller_active_page
    ON market_delivery_orders(branch_store_id,created_at_millis DESC,id DESC)
    WHERE status NOT IN ('DELIVERED','CANCELLED');
CREATE INDEX market_delivery_courier_active_page
    ON market_delivery_orders(courier_id,created_at_millis DESC,id DESC)
    WHERE courier_id IS NOT NULL AND status NOT IN ('DELIVERED','CANCELLED');
