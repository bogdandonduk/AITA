-- Company employment is NOT store membership and grants no stock, billing or account-security access.
CREATE TABLE company_departments (
    id TEXT PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    title JSONB NOT NULL CHECK(jsonb_typeof(title)='object'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE company_jobs (
    id TEXT PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_.-]{1,63}$'),
    department_id TEXT REFERENCES company_departments(id),
    title JSONB NOT NULL CHECK(jsonb_typeof(title)='object'),
    description JSONB NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(description)='object'),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE company_capabilities (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL
);
CREATE TABLE company_job_capabilities (
    job_id TEXT NOT NULL REFERENCES company_jobs(id),
    capability_id TEXT NOT NULL REFERENCES company_capabilities(id),
    PRIMARY KEY(job_id,capability_id)
);
CREATE TABLE company_employments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    employee_number TEXT UNIQUE,
    status TEXT NOT NULL DEFAULT 'active' CHECK(status IN ('active','suspended','ended')),
    starts_at_millis BIGINT NOT NULL,
    ends_at_millis BIGINT,
    manager_employment_id UUID REFERENCES company_employments(id),
    metadata JSONB NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(metadata)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK(ends_at_millis IS NULL OR ends_at_millis>starts_at_millis)
);
CREATE INDEX company_employment_user_status ON company_employments(user_id,status);
CREATE UNIQUE INDEX company_one_current_employment ON company_employments(user_id) WHERE status<>'ended';
CREATE TABLE company_job_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employment_id UUID NOT NULL REFERENCES company_employments(id),
    job_id TEXT NOT NULL REFERENCES company_jobs(id),
    starts_at_millis BIGINT NOT NULL,
    ends_at_millis BIGINT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CHECK(ends_at_millis IS NULL OR ends_at_millis>starts_at_millis)
);
CREATE INDEX company_assignments_employment ON company_job_assignments(employment_id,is_active);
CREATE TABLE company_employment_events (
    id BIGSERIAL PRIMARY KEY,
    source_table TEXT NOT NULL,
    operation TEXT NOT NULL,
    actor_user_id UUID,
    database_actor TEXT NOT NULL,
    reason TEXT NOT NULL,
    before_snapshot JSONB,
    after_snapshot JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
-- Keep authority stable while an authorized support transaction runs. Admin statements acquire
-- the exclusive lock; API transactions acquire its shared counterpart before reading permissions.
CREATE FUNCTION aita_company_authority_lock() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended('aita-company-authority-v1',0));
    RETURN NULL;
END $$;
CREATE FUNCTION aita_company_updated_at() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at := clock_timestamp(); RETURN NEW; END $$;
DO $$ DECLARE t TEXT; BEGIN
    FOREACH t IN ARRAY ARRAY['company_jobs','company_employments','company_job_assignments'] LOOP
        EXECUTE format('CREATE TRIGGER company_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION aita_company_updated_at()',t);
    END LOOP;
END $$;
CREATE FUNCTION aita_company_employment_audit() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE actor UUID;
BEGIN
    BEGIN actor := nullif(current_setting('aita.operator_user_id',TRUE),'')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN actor := NULL; END;
    INSERT INTO company_employment_events(source_table,operation,actor_user_id,database_actor,reason,before_snapshot,after_snapshot)
    VALUES(TG_TABLE_NAME,TG_OP,actor,current_user,
        coalesce(nullif(current_setting('aita.operator_reason',TRUE),''),'database administration'),
        CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD) END,
        CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW) END);
    RETURN NULL;
END $$;
DO $$ DECLARE t TEXT; BEGIN
    FOREACH t IN ARRAY ARRAY['company_departments','company_jobs','company_job_capabilities','company_employments','company_job_assignments'] LOOP
        EXECUTE format('CREATE TRIGGER company_authority_lock BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH STATEMENT EXECUTE FUNCTION aita_company_authority_lock()',t);
        EXECUTE format('CREATE TRIGGER company_authority_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION aita_company_employment_audit()',t);
    END LOOP;
END $$;

INSERT INTO company_departments(id,title) VALUES('support','{"en":"Customer support","ru":"Поддержка клиентов","kk":"Клиенттерді қолдау"}');
INSERT INTO company_jobs(id,department_id,title) VALUES
 ('support_agent','support','{"en":"Support agent","ru":"Специалист поддержки","kk":"Қолдау маманы"}'),
 ('support_supervisor','support','{"en":"Support supervisor","ru":"Руководитель поддержки","kk":"Қолдау жетекшісі"}');
INSERT INTO company_capabilities(id,description) VALUES
 ('support.queue.read','View support queue and customer conversations'),
 ('support.ticket.claim','Claim unassigned support work'),
 ('support.message.reply','Reply to assigned customer conversations'),
 ('support.ticket.resolve','Resolve and reopen assigned conversations'),
 ('support.ticket.manage','Release/reassign work and manage another agent''s ticket'),
 ('support.metrics.read','View team support metrics');
INSERT INTO company_job_capabilities(job_id,capability_id)
SELECT 'support_agent',id FROM company_capabilities WHERE id NOT IN ('support.ticket.manage','support.metrics.read');
INSERT INTO company_job_capabilities(job_id,capability_id) SELECT 'support_supervisor',id FROM company_capabilities;
-- Time-aware SQL projection, also used to find tickets whose former assignee lost access.
CREATE VIEW company_support_agent_access AS
SELECT DISTINCT e.user_id FROM company_employments e
JOIN users u ON u.id=e.user_id AND u.is_active
JOIN company_job_assignments a ON a.employment_id=e.id AND a.is_active
JOIN company_jobs j ON j.id=a.job_id AND j.is_active
JOIN company_job_capabilities c ON c.job_id=j.id AND c.capability_id='support.queue.read'
LEFT JOIN company_departments d ON d.id=j.department_id
WHERE e.status='active' AND (d.id IS NULL OR d.is_active)
  AND e.starts_at_millis<=floor(extract(epoch FROM statement_timestamp())*1000)::bigint
  AND (e.ends_at_millis IS NULL OR e.ends_at_millis>floor(extract(epoch FROM statement_timestamp())*1000)::bigint)
  AND a.starts_at_millis<=floor(extract(epoch FROM statement_timestamp())*1000)::bigint
  AND (a.ends_at_millis IS NULL OR a.ends_at_millis>floor(extract(epoch FROM statement_timestamp())*1000)::bigint);

-- No user, store owner, or developer account is automatically employed.

ALTER TABLE support_tickets ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
-- Deterministic history order, independent of heap order during ALTER TABLE.
ALTER TABLE support_messages ADD COLUMN sequence BIGINT;
CREATE SEQUENCE support_message_sequence OWNED BY support_messages.sequence;
WITH ordered AS (
    SELECT id,row_number() OVER (ORDER BY created_at_millis,id) AS ordinal FROM support_messages
) UPDATE support_messages m SET sequence=o.ordinal FROM ordered o WHERE m.id=o.id;
SELECT setval('support_message_sequence',coalesce((SELECT max(sequence) FROM support_messages),1),
              EXISTS(SELECT 1 FROM support_messages));
ALTER TABLE support_messages ALTER COLUMN sequence SET NOT NULL,
    ALTER COLUMN sequence SET DEFAULT nextval('support_message_sequence');
CREATE UNIQUE INDEX support_message_sequence_unique ON support_messages(sequence);
CREATE INDEX support_messages_ticket_sequence ON support_messages(ticket_id,sequence DESC) WHERE is_active;
CREATE TABLE support_commands (
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    command_id TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    message_id UUID REFERENCES support_messages(id) ON DELETE CASCADE,
    created_at_millis BIGINT NOT NULL,
    PRIMARY KEY(actor_user_id,command_id)
);
CREATE TABLE support_ticket_events (
    id BIGSERIAL PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES support_tickets(id) ON DELETE CASCADE,
    actor_user_id UUID,
    employment_id UUID REFERENCES company_employments(id),
    event_type TEXT NOT NULL,
    message_id UUID,
    previous_assignee_user_id UUID,
    next_assignee_user_id UUID,
    previous_status TEXT,
    next_status TEXT,
    occurred_at_millis BIGINT NOT NULL
);
CREATE INDEX support_ticket_events_history ON support_ticket_events(ticket_id,id);
CREATE TABLE company_employee_activity (
    id BIGSERIAL PRIMARY KEY,
    employment_id UUID NOT NULL REFERENCES company_employments(id),
    actor_user_id UUID NOT NULL,
    metric_key TEXT NOT NULL,
    source_id TEXT NOT NULL,
    value BIGINT NOT NULL DEFAULT 1,
    occurred_at_millis BIGINT NOT NULL,
    UNIQUE(employment_id,metric_key,source_id)
);
CREATE INDEX company_employee_activity_period ON company_employee_activity(employment_id,occurred_at_millis);
-- Only facts written by the server become metrics; no client-supplied performance counters.
CREATE VIEW company_employee_daily_metrics AS
SELECT employment_id,metric_key,(to_timestamp(occurred_at_millis/1000.0) AT TIME ZONE 'UTC')::date AS day_utc,
       count(*) AS samples,sum(value) AS total_value
FROM company_employee_activity GROUP BY employment_id,metric_key,day_utc;
