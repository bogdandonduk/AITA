CREATE TABLE market_price_watches (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    settings JSONB NOT NULL,
    observations JSONB NOT NULL DEFAULT '[]'::jsonb,
    checked_at_millis BIGINT,
    next_check_at_millis BIGINT,
    updated_at_millis BIGINT NOT NULL,
    request_id UUID NOT NULL,
    request_fingerprint TEXT NOT NULL,
    PRIMARY KEY (user_id, store_id),
    CHECK (jsonb_typeof(settings) = 'object'),
    CHECK (jsonb_typeof(observations) = 'array' AND jsonb_array_length(observations) <= 10),
    CHECK (NOT enabled OR next_check_at_millis IS NOT NULL)
);
CREATE INDEX market_price_watches_due ON market_price_watches(next_check_at_millis, user_id, store_id) WHERE enabled;
