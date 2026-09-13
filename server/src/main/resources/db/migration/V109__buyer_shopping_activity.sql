-- Historical PUBLIC buyer-intent labels, captured with the command's existing atomic outcome.
-- Older command rows stay untouched; do not infer past names from today's catalogue or alter hashes.
ALTER TABLE buyer_shopping_commands
    ADD COLUMN activity_details JSONB,
    ADD CONSTRAINT buyer_shopping_activity_shape CHECK (
        activity_details IS NULL OR (
            accepted AND jsonb_typeof(activity_details)='object'
            AND activity_details ? 'lines' AND jsonb_typeof(activity_details->'lines')='array'
            AND jsonb_array_length(activity_details->'lines') BETWEEN 0 AND 50
        )
    );
-- Stable newest-first windows, including commands with the same timestamp.
CREATE INDEX buyer_shopping_activity_order
    ON buyer_shopping_commands(user_id,created_at_millis DESC,command_id DESC);
