-- Private account bookmarks. Public shop access is re-evaluated on every directory read.
CREATE TABLE buyer_saved_shop_states (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0)
);
CREATE TABLE buyer_saved_shops (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY (user_id, store_id)
);
