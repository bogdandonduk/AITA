-- Retain only an inactive reference for receipts, payroll and immutable audit records.
ALTER TABLE users ADD COLUMN deleted_at_millis bigint;
CREATE FUNCTION aita_guard_deleted_store_owner() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner text; deleted bigint;
BEGIN
  FOR owner IN SELECT jsonb_array_elements_text(NEW.owner_user_ids) LOOP
    SELECT deleted_at_millis INTO deleted FROM users WHERE id=owner::uuid FOR SHARE;
    IF deleted IS NOT NULL THEN RAISE EXCEPTION 'Deleted account cannot own a store'; END IF;
  END LOOP;
  RETURN NEW;
END $$;
CREATE TRIGGER aita_guard_deleted_store_owner BEFORE INSERT OR UPDATE OF owner_user_ids ON stores
FOR EACH ROW EXECUTE FUNCTION aita_guard_deleted_store_owner();

CREATE FUNCTION aita_guard_deleted_supplier_owner() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE owner text; deleted bigint;
BEGIN
  FOR owner IN SELECT jsonb_array_elements_text(COALESCE(NULLIF(NEW.user_ids,''),'[]')::jsonb) LOOP
    SELECT deleted_at_millis INTO deleted FROM users WHERE id=owner::uuid FOR SHARE;
    IF deleted IS NOT NULL THEN RAISE EXCEPTION 'Deleted account cannot own a supplier'; END IF;
  END LOOP;
  RETURN NEW;
END $$;
CREATE TRIGGER aita_guard_deleted_supplier_owner BEFORE INSERT OR UPDATE OF user_ids ON suppliers
FOR EACH ROW EXECUTE FUNCTION aita_guard_deleted_supplier_owner();

CREATE TRIGGER aita_guard_deleted_manufacturer_owner BEFORE INSERT OR UPDATE OF user_ids ON manufacturers
FOR EACH ROW EXECUTE FUNCTION aita_guard_deleted_supplier_owner();

ALTER TABLE auth_security_email_challenges DROP CONSTRAINT auth_security_email_challenges_action_check;
ALTER TABLE auth_security_email_challenges ADD CONSTRAINT auth_security_email_challenges_action_check
CHECK (action IN ('LOGIN_POLICY','ADD_EMAIL','REMOVE_EMAIL','TOTP_SETUP','PROFILE','ACCOUNT_DELETE'));
