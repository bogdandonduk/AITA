-- Unknown historical modification dates stay NULL; do not invent an edit time.
ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;
ALTER TABLE suppliers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

CREATE FUNCTION aita_record_audit_dates() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.updated_at := COALESCE((to_jsonb(NEW)->>TG_ARGV[0])::timestamptz, clock_timestamp());
  ELSE
    -- Creation belongs to the original record and cannot be rewritten by an edit.
    NEW := jsonb_populate_record(NEW, jsonb_build_object(TG_ARGV[0], to_jsonb(OLD)->TG_ARGV[0]));
    IF (to_jsonb(NEW) - 'updated_at') IS DISTINCT FROM (to_jsonb(OLD) - 'updated_at') THEN
      NEW.updated_at := GREATEST(clock_timestamp(), OLD.updated_at + INTERVAL '1 millisecond');
    ELSE
      NEW.updated_at := OLD.updated_at;
    END IF;
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER users_audit_dates BEFORE INSERT OR UPDATE ON users FOR EACH ROW EXECUTE FUNCTION aita_record_audit_dates('created_at');
CREATE TRIGGER stores_audit_dates BEFORE INSERT OR UPDATE ON stores FOR EACH ROW EXECUTE FUNCTION aita_record_audit_dates('created_at');
CREATE TRIGGER suppliers_audit_dates BEFORE INSERT OR UPDATE ON suppliers FOR EACH ROW EXECUTE FUNCTION aita_record_audit_dates('added_at');
CREATE TRIGGER worker_memberships_audit_dates BEFORE INSERT OR UPDATE ON store_worker_memberships FOR EACH ROW EXECUTE FUNCTION aita_record_audit_dates('created_at');
CREATE TRIGGER worker_requests_audit_dates BEFORE INSERT OR UPDATE ON store_worker_requests FOR EACH ROW EXECUTE FUNCTION aita_record_audit_dates('created_at');

-- Existing buyer creation can be recovered from its immutable creation event.
UPDATE store_buyers b SET payload = jsonb_set(b.payload, '{createdAtMillis}', to_jsonb(a.created))
FROM (SELECT entity_id, min(created_at_millis) AS created FROM operation_logs
      WHERE entity_type='buyer' AND action='created' GROUP BY entity_id) a
WHERE b.id::text=a.entity_id AND COALESCE((b.payload->>'createdAtMillis')::bigint,0)=0;
