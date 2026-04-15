create table if not exists realtime_updates(
    user_id uuid primary key,
    update_ids jsonb not null default '[]'::jsonb
);