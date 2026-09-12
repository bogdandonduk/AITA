-- Additive only: keep unknown/custom and text-only legacy history intact.
ALTER TABLE user_notifications
    ADD COLUMN message_template jsonb,
    ADD COLUMN title_template jsonb,
    ADD COLUMN message_translations jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN title_translations jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE operation_logs
    ADD COLUMN title_template jsonb,
    ADD COLUMN details_template jsonb;

-- Existing history is resolved conservatively at read time. Do not infer event facts in SQL,
-- rewrite audit timestamps, or delete text whose original message type cannot be established.
