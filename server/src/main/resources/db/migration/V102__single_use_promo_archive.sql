-- Keep the hashed identity as a tombstone so a used secret can never be issued again.
-- The redeemable pool is active, never-used rows; immutable use snapshots live separately.
-- Bindings are historical identities, not cascade owners. Deleting a location/account must
-- neither delete a consumed secret nor be blocked by its immutable audit snapshot.
ALTER TABLE subscription_promocodes
    DROP CONSTRAINT subscription_promocodes_bound_store_id_fkey,
    DROP CONSTRAINT subscription_promocodes_bound_owner_id_fkey;
ALTER TABLE subscription_promo_redemptions
    ADD COLUMN billing_owner_user_id UUID,
    ADD COLUMN session_id UUID,
    ADD COLUMN charge_minor BIGINT CHECK (charge_minor >= 0),
    ADD COLUMN regular_price_minor BIGINT CHECK (regular_price_minor >= 0),
    ADD COLUMN currency_code TEXT,
    ADD COLUMN granted_from_millis BIGINT,
    ADD COLUMN request_hash TEXT;

CREATE TABLE used_subscription_promocodes (
    promo_id UUID PRIMARY KEY REFERENCES subscription_promocodes(id),
    code_hash TEXT NOT NULL UNIQUE,
    promo_snapshot JSONB NOT NULL,
    first_redemption_id UUID,
    first_used_at_millis BIGINT,
    last_used_at_millis BIGINT,
    actor_user_id UUID,
    billing_owner_user_id UUID,
    store_id UUID,
    session_id UUID,
    command_id UUID,
    kind TEXT NOT NULL,
    granted_from_millis BIGINT,
    granted_until_millis BIGINT,
    charge_minor BIGINT,
    regular_price_minor BIGINT,
    discount_applied_minor BIGINT,
    currency_code TEXT,
    request_hash TEXT,
    -- Existing multi-use history is retained, not falsified as a single historical use.
    legacy_redemption_count BIGINT NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX used_subscription_promocodes_actor_time ON used_subscription_promocodes(actor_user_id, first_used_at_millis DESC);
CREATE INDEX used_subscription_promocodes_store_time ON used_subscription_promocodes(store_id, first_used_at_millis DESC);

INSERT INTO used_subscription_promocodes
    (promo_id,code_hash,promo_snapshot,first_redemption_id,first_used_at_millis,last_used_at_millis,
     actor_user_id,store_id,command_id,kind,granted_until_millis,discount_applied_minor,legacy_redemption_count)
SELECT p.id,p.code_hash,to_jsonb(p),f.id,f.redeemed_at_millis,
       (SELECT max(r.redeemed_at_millis) FROM subscription_promo_redemptions r WHERE r.promo_id=p.id),
       f.actor_user_id,f.store_id,f.command_id,p.kind,f.granted_until_millis,f.discount_applied_minor,
       greatest(p.redemption_count,(SELECT count(*) FROM subscription_promo_redemptions r WHERE r.promo_id=p.id))
FROM subscription_promocodes p LEFT JOIN LATERAL
    (SELECT * FROM subscription_promo_redemptions r WHERE r.promo_id=p.id ORDER BY redeemed_at_millis,id LIMIT 1) f ON TRUE
WHERE p.redemption_count>0 OR f.id IS NOT NULL;

UPDATE subscription_promocodes p SET is_active=FALSE,
    redemption_count=greatest(p.redemption_count,u.legacy_redemption_count),
    max_redemptions=greatest(p.max_redemptions,u.legacy_redemption_count)
FROM used_subscription_promocodes u WHERE p.id=u.promo_id;
UPDATE subscription_promocodes SET max_redemptions=1 WHERE redemption_count=0;

CREATE FUNCTION aita_single_use_promo_guard() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' AND (NEW.redemption_count<>0 OR NEW.max_redemptions<>1) THEN
        RAISE EXCEPTION 'New promo codes are single use';
    END IF;
    IF TG_OP='UPDATE' THEN
        IF NEW.id<>OLD.id OR NEW.code_hash<>OLD.code_hash THEN
            RAISE EXCEPTION 'Promo identity is immutable';
        END IF;
        IF OLD.redemption_count>0 AND NEW IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION 'Used promo codes are immutable';
        END IF;
        IF OLD.redemption_count=0 AND (NEW.max_redemptions<>1 OR NEW.redemption_count NOT IN (0,1)) THEN
            RAISE EXCEPTION 'Promo codes are single use';
        END IF;
        IF NEW.redemption_count=1 AND (NEW.is_active OR NOT EXISTS
            (SELECT 1 FROM used_subscription_promocodes WHERE promo_id=NEW.id)) THEN
            RAISE EXCEPTION 'Consumed promo requires an archived redemption';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER subscription_promocode_single_use_guard BEFORE INSERT OR UPDATE ON subscription_promocodes
FOR EACH ROW EXECUTE FUNCTION aita_single_use_promo_guard();

CREATE FUNCTION aita_archive_promo_redemption() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE p subscription_promocodes%ROWTYPE;
BEGIN
    SELECT * INTO p FROM subscription_promocodes WHERE id=NEW.promo_id FOR UPDATE;
    IF NOT FOUND OR NOT p.is_active OR p.redemption_count<>0 OR
       EXISTS(SELECT 1 FROM used_subscription_promocodes WHERE promo_id=p.id) THEN
        RAISE EXCEPTION 'Promo code already consumed or unavailable' USING ERRCODE='23505';
    END IF;
    INSERT INTO used_subscription_promocodes
        (promo_id,code_hash,promo_snapshot,first_redemption_id,first_used_at_millis,last_used_at_millis,
         actor_user_id,billing_owner_user_id,store_id,session_id,command_id,kind,granted_from_millis,
         granted_until_millis,charge_minor,regular_price_minor,discount_applied_minor,currency_code,request_hash)
    VALUES (p.id,p.code_hash,to_jsonb(p),NEW.id,NEW.redeemed_at_millis,NEW.redeemed_at_millis,
         NEW.actor_user_id,NEW.billing_owner_user_id,NEW.store_id,NEW.session_id,NEW.command_id,NEW.kind,
         NEW.granted_from_millis,NEW.granted_until_millis,NEW.charge_minor,NEW.regular_price_minor,
         NEW.discount_applied_minor,NEW.currency_code,NEW.request_hash);
    UPDATE subscription_promocodes SET redemption_count=1,is_active=FALSE WHERE id=p.id;
    RETURN NEW;
END $$;
CREATE TRIGGER subscription_promo_archive AFTER INSERT ON subscription_promo_redemptions
FOR EACH ROW EXECUTE FUNCTION aita_archive_promo_redemption();

CREATE FUNCTION aita_used_promo_immutable() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Used promo archive is immutable'; END $$;
CREATE TRIGGER used_subscription_promocodes_immutable BEFORE UPDATE OR DELETE ON used_subscription_promocodes
FOR EACH ROW EXECUTE FUNCTION aita_used_promo_immutable();
