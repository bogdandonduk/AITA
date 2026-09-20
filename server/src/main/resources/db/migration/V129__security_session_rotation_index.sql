CREATE INDEX IF NOT EXISTS refresh_sessions_user_rotation_idx ON refresh_sessions(user_id, rotated_from);
