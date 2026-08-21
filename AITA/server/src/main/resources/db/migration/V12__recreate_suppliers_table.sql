create table if not exists suppliers (
  id uuid primary key,
  user_ids text,

  type_id uuid not null,

  name text not null,
  extra_names text,

  phone_numbers text not null,
  emails text not null,
  added_at timestamptz not null default now(),
  is_active boolean not null
);