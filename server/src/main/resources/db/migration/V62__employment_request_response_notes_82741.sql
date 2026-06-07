ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS note_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS response_note TEXT NULL;

ALTER TABLE store_worker_requests
    ADD COLUMN IF NOT EXISTS response_note_localized JSONB NOT NULL DEFAULT '[]'::jsonb;

UPDATE store_worker_requests
SET response_note = note
WHERE status IN ('accepted', 'declined')
  AND response_note IS NULL
  AND note IS NOT NULL
  AND btrim(note) <> '';

UPDATE store_worker_requests
SET response_note_localized = jsonb_build_array(jsonb_build_object('language', 'main', 'value', response_note))
WHERE status IN ('accepted', 'declined')
  AND response_note IS NOT NULL
  AND btrim(response_note) <> ''
  AND (response_note_localized IS NULL OR response_note_localized = '[]'::jsonb);

UPDATE store_worker_requests
SET note_localized = jsonb_build_array(jsonb_build_object('language', 'main', 'value', note))
WHERE status IN ('pending', 'invited')
  AND note IS NOT NULL
  AND btrim(note) <> ''
  AND (note_localized IS NULL OR note_localized = '[]'::jsonb);

UPDATE store_worker_requests
SET note_localized = COALESCE(note_localized, '[]'::jsonb),
    response_note_localized = COALESCE(response_note_localized, '[]'::jsonb);
