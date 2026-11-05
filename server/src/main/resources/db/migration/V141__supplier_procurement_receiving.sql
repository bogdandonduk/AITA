-- Durable request identity makes retries safe; received totals must not follow shrinking stock.
ALTER TABLE supplier_order_lines ADD COLUMN received_quantity_total DOUBLE PRECISION;
ALTER TABLE supplier_order_lines ADD CONSTRAINT supplier_received_quantity_nonnegative
    CHECK (received_quantity_total IS NULL OR (received_quantity_total >= 0 AND received_quantity_total < 'Infinity'::float8));
CREATE TABLE supplier_order_commands (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES supplier_orders(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('create', 'receive')),
    fingerprint TEXT NOT NULL,
    created_at_millis BIGINT NOT NULL
);
CREATE INDEX supplier_order_commands_order_idx ON supplier_order_commands(order_id);
