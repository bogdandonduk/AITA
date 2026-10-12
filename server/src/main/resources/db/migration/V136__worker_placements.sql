CREATE TABLE worker_placement_commands (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request JSONB NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX worker_placement_commands_subject ON worker_placement_commands(subject_user_id);
