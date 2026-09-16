-- Small, normalized, account-owned pictures. Null jpeg is a tombstone retaining its optimistic revision.
CREATE TABLE user_profile_photos (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    jpeg BYTEA CHECK (jpeg IS NULL OR octet_length(jpeg) BETWEEN 1 AND 524288),
    updated_at_millis BIGINT NOT NULL CHECK (updated_at_millis >= 0)
);
