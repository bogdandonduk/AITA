create table if not exists suppliers (
  id uuid primary key,
  type_id text not null,
  phone_numbers text not null,
  emails text not null,
  added_at timestamptz not null default now(),
  is_active boolean not null
);