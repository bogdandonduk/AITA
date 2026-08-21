-- Email-confirmed phone login aliases. The alias is a login name, not an SMS destination.
-- Additive only. Do not edit after it has been applied.

CREATE TABLE IF NOT EXISTS auth_phone_alias_challenges (
    id UUID PRIMARY KEY,
    challenge_public_id UUID NOT NULL UNIQUE REFERENCES auth_one_time_challenges(public_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL CHECK (action IN ('ADD_OR_REPLACE', 'REMOVE')),
    requested_phone_alias VARCHAR(32) NULL,
    consumed_at_millis BIGINT NULL,
    created_at_millis BIGINT NOT NULL,
    CHECK (
        (action = 'REMOVE' AND requested_phone_alias IS NULL) OR
        (action = 'ADD_OR_REPLACE' AND requested_phone_alias IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_auth_phone_alias_challenges_user_time
    ON auth_phone_alias_challenges(user_id, created_at_millis DESC);
