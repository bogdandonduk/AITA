create table if not exists users (
  id uuid primary key,
  phone_number varchar(255) unique not null,
  email varchar(255) unique not null,
  first_name varchar(255) not null,
  last_name varchar(255) not null,
  country_locale varchar(255) not null,
  password_hash varchar(100) not null,
  created_at timestamptz not null default now(),
  is_active boolean not null default true
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