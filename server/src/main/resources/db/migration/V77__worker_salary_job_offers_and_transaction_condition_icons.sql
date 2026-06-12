ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS job_title TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS job_title_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS salary TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS salary_currency_code TEXT NOT NULL DEFAULT 'KZT',
    ADD COLUMN IF NOT EXISTS offer_note TEXT,
    ADD COLUMN IF NOT EXISTS offer_note_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE store_worker_memberships
    ADD COLUMN IF NOT EXISTS job_title TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS job_title_localized JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS salary TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS salary_currency_code TEXT NOT NULL DEFAULT 'KZT';

UPDATE store_worker_requests
SET salary_currency_code = 'KZT'
WHERE salary_currency_code IS NULL OR trim(salary_currency_code) = '';

UPDATE store_worker_requests
SET offer_note_localized = '[]'::jsonb
WHERE offer_note_localized IS NULL;

UPDATE store_worker_memberships
SET salary_currency_code = 'KZT'
WHERE salary_currency_code IS NULL OR trim(salary_currency_code) = '';
