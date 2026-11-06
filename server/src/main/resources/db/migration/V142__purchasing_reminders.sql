-- Personal, explicitly enabled reminders. No stock writes or automatic supplier orders.
CREATE TABLE replenishment_reminders (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision > 0),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    settings JSONB NOT NULL,
    time_zone_id TEXT NOT NULL,
    days_before_order INTEGER NOT NULL CHECK (days_before_order BETWEEN 0 AND 30),
    due_at_millis BIGINT,
    fired_at_millis BIGINT,
    updated_at_millis BIGINT NOT NULL,
    request_id UUID NOT NULL,
    request_fingerprint TEXT NOT NULL,
    PRIMARY KEY (user_id, store_id),
    CHECK (NOT enabled OR due_at_millis IS NOT NULL)
);
CREATE INDEX replenishment_reminders_due_idx ON replenishment_reminders(due_at_millis)
    WHERE enabled AND fired_at_millis IS NULL;
