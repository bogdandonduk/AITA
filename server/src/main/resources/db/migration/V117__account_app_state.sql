CREATE TABLE account_app_states (
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope VARCHAR(64) NOT NULL,
    revision BIGINT NOT NULL CHECK (revision > 0),
    enabled BOOLEAN NOT NULL,
    document TEXT,
    updated_at_millis BIGINT NOT NULL,
    PRIMARY KEY (owner_user_id, scope),
    CHECK (octet_length(document) <= 262144),
    CHECK (enabled OR document IS NULL)
);
