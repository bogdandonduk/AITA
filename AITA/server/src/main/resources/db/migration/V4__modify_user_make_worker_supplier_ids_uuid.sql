create table if not exists users (
  id uuid primary key,
  phone_number varchar(255) unique not null,
  email varchar(255) unique not null,
  first_name varchar(255) not null,
  last_name varchar(255) not null,
  country_locale varchar(255) not null,
  store_worker_account_id uuid default null,
  store_supplier_account_id uuid default null,
  password_hash varchar(100) not null,
  created_at timestamptz not null default now(),
  is_active boolean not null default true
);

create table if not exists store_workers (
  id uuid primary key,
  privilege_mode_id int not null,
  is_active boolean not null,
  store_id uuid not null,
  store_sub_id uuid not null,
  salary varchar(255) not null,
  salary_currency varchar(255) not null,
  added_at timestamptz not null default now()
);

create table if not exists store_suppliers (
  id uuid primary key,
  is_active boolean not null,
  store_id varchar(255) not null,
  store_sub_id varchar(255) not null,
  added_at timestamptz not null default now()
);

create table if not exists refresh_sessions (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  token_hash char(64) not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  rotated_from uuid,
  revoked_at timestamptz,
  meta jsonb
);

create index if not exists idx_refresh_sessions_user_id on refresh_sessions(user_id);
create index if not exists idx_refresh_sessions_token_hash on refresh_sessions(token_hash);