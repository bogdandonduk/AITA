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
  created_at timestamptz not null default now()
);

create table if not exists store_subscriptions(
  id uuid primary key default gen_random_uuid(),
  store_id uuid unique not null,
  history jsonb not null default '[]'::jsonb
);

create table if not exists store_activation_history(
  id uuid primary key default gen_random_uuid(),
  store_id uuid unique not null,
  history jsonb not null default '[]'::jsonb
);

create table if not exists user_balances(
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique not null,
  history jsonb not null default '[]'::jsonb
);




