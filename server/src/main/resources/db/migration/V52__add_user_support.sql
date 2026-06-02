CREATE TABLE IF NOT EXISTS support_tickets (
    id UUID PRIMARY KEY,
    public_id TEXT NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    store_id UUID NULL,
    subject TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT 'general',
    priority TEXT NOT NULL DEFAULT 'normal',
    status TEXT NOT NULL DEFAULT 'open',
    assigned_agent_user_id UUID NULL,
    last_message TEXT NOT NULL DEFAULT '',
    last_message_at_millis BIGINT NOT NULL DEFAULT 0,
    last_customer_message_at_millis BIGINT NULL,
    last_agent_message_at_millis BIGINT NULL,
    unread_for_user_count INTEGER NOT NULL DEFAULT 0,
    unread_for_agent_count INTEGER NOT NULL DEFAULT 0,
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at_millis BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    closed_at_millis BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_support_tickets_user_updated ON support_tickets(user_id, updated_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_status_updated ON support_tickets(status, updated_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_support_tickets_assigned_status ON support_tickets(assigned_agent_user_id, status);
CREATE INDEX IF NOT EXISTS idx_support_tickets_store ON support_tickets(store_id);

CREATE TABLE IF NOT EXISTS support_messages (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sender_role TEXT NOT NULL DEFAULT 'customer',
    sender_display_name TEXT NOT NULL DEFAULT '',
    body TEXT NOT NULL,
    attachments JSONB NOT NULL DEFAULT '[]'::jsonb,
    meta JSONB NOT NULL DEFAULT '{}'::jsonb,
    client_message_id TEXT NULL UNIQUE,
    created_at_millis BIGINT NOT NULL,
    edited_at_millis BIGINT NULL,
    read_by_customer_at_millis BIGINT NULL,
    read_by_agent_at_millis BIGINT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_support_messages_ticket_created ON support_messages(ticket_id, created_at_millis ASC);
CREATE INDEX IF NOT EXISTS idx_support_messages_user_created ON support_messages(user_id, created_at_millis DESC);
CREATE INDEX IF NOT EXISTS idx_support_messages_sender_role ON support_messages(sender_role);
