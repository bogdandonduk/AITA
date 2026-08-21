create table if not exists stores (
  id uuid primary key,
  user_id uuid not null,
  name text not null,
  alias text,
  description text,
  company_form text not null,
  location text not null,
  phone_numbers text not null,
  emails text not null,
  created_at timestamptz not null default now(),
  is_active boolean not null
);