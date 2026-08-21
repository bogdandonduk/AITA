CREATE TABLE IF NOT EXISTS operation_logs (
    id UUID PRIMARY KEY,
    root_store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    store_public_id TEXT NOT NULL DEFAULT '',
    store_name JSONB NOT NULL DEFAULT '[]'::jsonb,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_public_id TEXT NOT NULL DEFAULT '',
    actor_display_name TEXT NOT NULL DEFAULT '',
    workshift_id UUID NULL REFERENCES workshifts(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NULL,
    title JSONB NOT NULL DEFAULT '[]'::jsonb,
    details JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at_millis BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS operation_logs_root_store_time_idx ON operation_logs(root_store_id, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS operation_logs_store_time_idx ON operation_logs(store_id, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS operation_logs_actor_time_idx ON operation_logs(actor_user_id, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS operation_logs_entity_idx ON operation_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS operation_logs_action_idx ON operation_logs(action);

-- Existing worker memberships may not have the newer logs_view permission.
-- Owners always have all permissions in code, but admin workers should see logs by default.
UPDATE store_worker_memberships
SET permissions = permissions || '["logs_view"]'::jsonb
WHERE role_id = 'admin'
  AND NOT (permissions ? 'logs_view');
