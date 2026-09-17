CREATE TABLE runtime_diagnostic_events (
    sequence BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    installation_id UUID NOT NULL,
    owner_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    received_at_millis BIGINT NOT NULL CHECK (received_at_millis > 0),
    report JSONB NOT NULL,
    country_code VARCHAR(2),
    region_code VARCHAR(32),
    CONSTRAINT runtime_diagnostic_object CHECK (jsonb_typeof(report) = 'object'),
    CONSTRAINT runtime_diagnostic_size CHECK (octet_length(report::text) <= 65536),
    CONSTRAINT runtime_diagnostic_identity CHECK (COALESCE(report->>'id' = event_id::text, false)),
    CONSTRAINT runtime_diagnostic_owner CHECK (COALESCE(
        (owner_user_id IS NULL AND report->'context'->'accountId' = 'null'::jsonb) OR
        (owner_user_id IS NOT NULL AND report->'context'->>'accountId' = owner_user_id::text), false)),
    CONSTRAINT runtime_diagnostic_country CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT runtime_diagnostic_location_consent CHECK (country_code IS NULL OR COALESCE(report->>'approximateLocation' = 'true', false)),
    CONSTRAINT runtime_diagnostic_region CHECK (region_code IS NULL OR (country_code IS NOT NULL AND region_code ~ '^[A-Za-z0-9 -]{1,32}$'))
);
CREATE INDEX runtime_diagnostics_received_idx ON runtime_diagnostic_events(received_at_millis);
CREATE INDEX runtime_diagnostics_installation_received_idx ON runtime_diagnostic_events(installation_id, received_at_millis);
CREATE INDEX runtime_diagnostics_owner_received_idx ON runtime_diagnostic_events(owner_user_id, received_at_millis);
