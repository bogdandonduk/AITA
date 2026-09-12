-- Entitlement belongs to the exact physical store_id, never implicitly to its children.
-- Existing paid periods remain intact. No branch receives a synthetic free subscription.
ALTER TABLE store_subscription_states
    ADD COLUMN access_kind TEXT NOT NULL DEFAULT 'paid',
    ADD COLUMN region_code TEXT NOT NULL DEFAULT 'KZ',
    ADD COLUMN renewal_price_minor BIGINT NOT NULL DEFAULT 799000 CHECK (renewal_price_minor >= 0),
    ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'KZT',
    ADD COLUMN renewal_period_unit TEXT NOT NULL DEFAULT 'month',
    ADD COLUMN renewal_period_count INTEGER NOT NULL DEFAULT 1 CHECK (renewal_period_count BETWEEN 1 AND 120),
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    ADD COLUMN next_attempt_at_millis BIGINT;

UPDATE store_subscription_states SET plan_id = 'basic' WHERE plan_id = 'standard_monthly_kzt';
ALTER TABLE store_subscription_states ADD CONSTRAINT subscription_access_kind_check
    CHECK (access_kind IN ('paid', 'timed', 'lifetime'));
ALTER TABLE store_subscription_states ADD CONSTRAINT subscription_lifetime_shape_check
    CHECK (access_kind <> 'lifetime' OR
           (plan_id = 'internal_lifetime' AND current_period_end_millis IS NULL AND
            next_charge_at_millis IS NULL AND auto_renew = FALSE AND renewal_price_minor = 0));

CREATE TABLE store_subscription_plan_prices (
    plan_id TEXT NOT NULL CHECK (plan_id = 'basic'),
    region_code TEXT NOT NULL CHECK (region_code ~ '^[A-Z]{2}$'),
    currency_code TEXT NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    price_minor BIGINT NOT NULL CHECK (price_minor BETWEEN 0 AND 1000000000000),
    period_unit TEXT NOT NULL DEFAULT 'month' CHECK (period_unit IN ('month', 'year')),
    period_count INTEGER NOT NULL DEFAULT 1 CHECK (period_count BETWEEN 1 AND 120),
    price_version BIGINT NOT NULL DEFAULT 1 CHECK (price_version > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (plan_id, region_code)
);
INSERT INTO store_subscription_plan_prices (plan_id, region_code, currency_code, price_minor)
VALUES ('basic', 'KZ', 'KZT', 799000);

-- Only SHA-256 of normalized, high-entropy ASCII codes is stored. Never persist plaintext codes.
CREATE TABLE subscription_promocodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash TEXT NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    kind TEXT NOT NULL CHECK (kind IN ('lifetime', 'timed', 'discount')),
    plan_id TEXT NOT NULL DEFAULT 'basic' CHECK (plan_id = 'basic'),
    duration_millis BIGINT CHECK (duration_millis BETWEEN 1 AND 3153600000000),
    discount_basis_points INTEGER CHECK (discount_basis_points BETWEEN 1 AND 10000),
    discount_minor BIGINT CHECK (discount_minor BETWEEN 1 AND 1000000000000),
    currency_code TEXT CHECK (currency_code ~ '^[A-Z]{3}$'),
    region_code TEXT CHECK (region_code ~ '^[A-Z]{2}$'),
    bound_store_id UUID REFERENCES stores(id) ON DELETE CASCADE,
    bound_owner_id UUID REFERENCES users(id) ON DELETE CASCADE,
    valid_from_millis BIGINT NOT NULL DEFAULT 0,
    valid_until_millis BIGINT,
    max_redemptions BIGINT NOT NULL DEFAULT 1 CHECK (max_redemptions > 0),
    redemption_count BIGINT NOT NULL DEFAULT 0 CHECK (redemption_count >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (valid_until_millis IS NULL OR valid_until_millis > valid_from_millis),
    CHECK (redemption_count <= max_redemptions),
    CHECK (
        (kind = 'lifetime' AND duration_millis IS NULL AND discount_basis_points IS NULL AND discount_minor IS NULL) OR
        (kind = 'timed' AND duration_millis IS NOT NULL AND discount_basis_points IS NULL AND discount_minor IS NULL) OR
        (kind = 'discount' AND duration_millis IS NULL AND
            ((discount_basis_points IS NOT NULL AND discount_minor IS NULL) OR
             (discount_basis_points IS NULL AND discount_minor IS NOT NULL AND currency_code IS NOT NULL)))
    )
);

CREATE TABLE subscription_commands (
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_hash TEXT NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    result_revision BIGINT NOT NULL,
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY (store_id, command_id)
);

CREATE TABLE subscription_promo_redemptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_id UUID NOT NULL REFERENCES subscription_promocodes(id),
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    command_id UUID NOT NULL,
    kind TEXT NOT NULL,
    granted_until_millis BIGINT,
    discount_applied_minor BIGINT NOT NULL DEFAULT 0 CHECK (discount_applied_minor >= 0),
    redeemed_at_millis BIGINT NOT NULL,
    UNIQUE (promo_id, store_id),
    UNIQUE (store_id, command_id)
);

ALTER TABLE store_subscription_charge_events ADD COLUMN command_id UUID;
CREATE UNIQUE INDEX subscription_charge_command_unique
    ON store_subscription_charge_events (store_id, command_id) WHERE command_id IS NOT NULL;
CREATE INDEX subscription_renewal_due_idx
    ON store_subscription_states (next_charge_at_millis, next_attempt_at_millis) WHERE auto_renew;
CREATE INDEX subscription_nonrenewing_expiry_idx
    ON store_subscription_states (current_period_end_millis, store_id)
    WHERE NOT auto_renew AND status = 'active' AND access_kind IN ('paid', 'timed');

-- A price editor cannot accidentally preserve a stale checkout version.
CREATE FUNCTION aita_subscription_price_version() RETURNS TRIGGER AS $$
BEGIN
    IF ROW(NEW.price_minor, NEW.currency_code, NEW.period_unit, NEW.period_count, NEW.is_active)
       IS DISTINCT FROM ROW(OLD.price_minor, OLD.currency_code, OLD.period_unit, OLD.period_count, OLD.is_active) THEN
        NEW.price_version := OLD.price_version + 1;
    ELSIF NEW.price_version < OLD.price_version THEN
        RAISE EXCEPTION 'Subscription price version cannot decrease';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER store_subscription_price_version_before_update
BEFORE UPDATE ON store_subscription_plan_prices
FOR EACH ROW EXECUTE FUNCTION aita_subscription_price_version();
