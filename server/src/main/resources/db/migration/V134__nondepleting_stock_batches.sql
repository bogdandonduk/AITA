ALTER TABLE stock_batches DROP CONSTRAINT stock_batches_kind_check;
ALTER TABLE stock_batches ADD CONSTRAINT stock_batches_kind_check CHECK (kind IN ('NORMAL','RETURNED','UNIVERSAL','UNLIMITED'));
