-- Buyer intent only. No stock allocation, payment, reservation, or order is created by these tables.
CREATE TABLE buyer_shopping_lists (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision>=0),
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL
);
CREATE TABLE buyer_shopping_lines (
    user_id UUID NOT NULL REFERENCES buyer_shopping_lists(user_id) ON DELETE CASCADE,
    -- Deliberately no listing/store FK: a withdrawn/deleted listing must not erase buyer intent.
    offer_id UUID NOT NULL,
    store_id UUID NOT NULL,
    public_title TEXT NOT NULL CHECK (char_length(public_title) BETWEEN 1 AND 180),
    public_shop_name TEXT NOT NULL CHECK (char_length(public_shop_name) BETWEEN 1 AND 120),
    units INTEGER NOT NULL CHECK (units BETWEEN 1 AND 999),
    basis JSONB NOT NULL CHECK (jsonb_typeof(basis)='object'),
    unit_name JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(unit_name)='array'),
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    PRIMARY KEY (user_id,offer_id)
);
CREATE TABLE buyer_shopping_commands (
    user_id UUID NOT NULL REFERENCES buyer_shopping_lists(user_id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    session_id UUID,
    offer_id UUID NOT NULL,
    requested_units INTEGER NOT NULL CHECK (requested_units BETWEEN 0 AND 999),
    expected_revision BIGINT NOT NULL CHECK (expected_revision>=0),
    accepted BOOLEAN NOT NULL,
    error_key TEXT,
    applied_revision BIGINT,
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY (user_id,command_id),
    CHECK ((accepted AND error_key IS NULL AND applied_revision IS NOT NULL)
        OR (NOT accepted AND error_key IS NOT NULL AND applied_revision IS NULL))
);
CREATE INDEX buyer_shopping_commands_time ON buyer_shopping_commands(user_id,created_at_millis DESC);
CREATE FUNCTION aita_buyer_shopping_command_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Shopping-list command outcomes are immutable'; END $$;
CREATE TRIGGER buyer_shopping_command_immutable BEFORE UPDATE ON buyer_shopping_commands
FOR EACH ROW EXECUTE FUNCTION aita_buyer_shopping_command_immutable();
