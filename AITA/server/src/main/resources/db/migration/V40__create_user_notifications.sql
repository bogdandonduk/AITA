CREATE TABLE IF NOT EXISTS user_notifications (
  id TEXT PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  type TEXT NOT NULL,
  category TEXT NOT NULL,
  created_at_millis BIGINT NOT NULL,
  shown_at_millis BIGINT NOT NULL,
  read_at_millis BIGINT NULL,
  source TEXT NOT NULL DEFAULT 'app',
  meta JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_created
  ON user_notifications(user_id, created_at_millis DESC);

CREATE INDEX IF NOT EXISTS idx_user_notifications_user_read
  ON user_notifications(user_id, read_at_millis);
