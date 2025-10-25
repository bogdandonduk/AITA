create table if not exists stock(
    id uuid primary key,
    user_id uuid not null,
    store_id uuid not null,

    barcode jsonb not null default '[]'::jsonb,
    name jsonb not null default '[]'::jsonb,

    quantity jsonb not null default '[]'::jsonb,

    category_ids jsonb not null default '[]'::jsonb,

    sale_prices jsonb not null default '[]'::jsonb,
    return_prices jsonb not null default '[]'::jsonb,
    supply_prices jsonb not null default '[]'::jsonb,

    created_at timestamptz not null default now(),
    is_active boolean not null default true
);