create table if not exists stores(
  id uuid primary key default gen_random_uuid(),
  owner_user_ids jsonb not null default '[]'::jsonb,
  store_type_ids jsonb not null default '[]'::jsonb,
  name jsonb not null default '[]'::jsonb,
  alias jsonb not null default '[]'::jsonb,
  description jsonb not null default '[]'::jsonb,
  company_forms jsonb not null default '[]'::jsonb,
  location jsonb not null default '{}'::jsonb,
  phone_numbers jsonb not null default '[]'::jsonb,
  emails jsonb not null default '[]'::jsonb,
  country_locales jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  is_active boolean not null
);