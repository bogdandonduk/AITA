-- Private store contacts and immutable quantity deductions. Historical snapshots survive contact edits.
CREATE TABLE store_buyers (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    payload JSONB NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0)
);
CREATE INDEX store_buyers_store ON store_buyers(store_id);
CREATE UNIQUE INDEX store_buyers_parent_copy ON store_buyers(store_id,(payload->>'sourceParentBuyerId')) WHERE payload->>'sourceParentBuyerId' IS NOT NULL;
CREATE TABLE store_buyer_commands (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL,
    request JSONB NOT NULL,
    result JSONB NOT NULL
);
CREATE TABLE stock_writeoffs (
    id UUID PRIMARY KEY,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    batch_id UUID NOT NULL,
    actor_id UUID NOT NULL,
    time_millis BIGINT NOT NULL,
    request JSONB NOT NULL,
    result JSONB NOT NULL
);
CREATE INDEX stock_writeoffs_store_time ON stock_writeoffs(store_id,time_millis DESC,id);
ALTER TABLE transactions ADD COLUMN buyer JSONB NULL;
CREATE FUNCTION aita_commerce_ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Commerce operation records are immutable'; END $$;
CREATE TRIGGER stock_writeoffs_immutable BEFORE UPDATE ON stock_writeoffs FOR EACH ROW EXECUTE FUNCTION aita_commerce_ledger_immutable();
CREATE TRIGGER buyer_commands_immutable BEFORE UPDATE ON store_buyer_commands FOR EACH ROW EXECUTE FUNCTION aita_commerce_ledger_immutable();
