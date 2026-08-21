create table if not exists user_balances(
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique not null,
  value text not null,
  currency_code text not null,
  history jsonb not null default '[]'::jsonb
);
