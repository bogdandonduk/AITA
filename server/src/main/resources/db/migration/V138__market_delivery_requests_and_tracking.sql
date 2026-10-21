-- Delivery workflow is independent of fiscal transactions: no payment or stock adjustment is
-- inferred from a customer request. Contact/location data is always behind authenticated reads.
CREATE TABLE market_delivery_policies (
    branch_store_id UUID PRIMARY KEY REFERENCES stores(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 0),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object')
);
CREATE TABLE market_delivery_orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    branch_store_id UUID REFERENCES stores(id) ON DELETE SET NULL,
    courier_id UUID REFERENCES users(id),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    status TEXT NOT NULL CHECK (status IN ('REQUESTED','CONFIRMED','PREPARING','READY','ON_THE_WAY','ARRIVED','DELIVERED','CANCELLED')),
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    handover_code TEXT NOT NULL CHECK (handover_code ~ '^[0-9]{6}$'),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    position JSONB,
    CHECK (branch_store_id IS NOT NULL OR status IN ('DELIVERED','CANCELLED'))
);
CREATE INDEX market_delivery_buyer_page ON market_delivery_orders(buyer_id, created_at_millis DESC, id DESC);
CREATE INDEX market_delivery_seller_page ON market_delivery_orders(branch_store_id, created_at_millis DESC, id DESC);
CREATE INDEX market_delivery_courier_page ON market_delivery_orders(courier_id, created_at_millis DESC, id DESC) WHERE courier_id IS NOT NULL;
CREATE TABLE market_delivery_commands (
    user_id UUID NOT NULL REFERENCES users(id),
    command_id UUID NOT NULL,
    request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    order_id UUID NOT NULL REFERENCES market_delivery_orders(id),
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY (user_id, command_id)
);
-- A retry can only acknowledge the same immutable intent.
CREATE TRIGGER market_delivery_command_immutable BEFORE UPDATE ON market_delivery_commands
FOR EACH ROW EXECUTE FUNCTION aita_buyer_shopping_command_immutable();
