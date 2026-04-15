create table if not exists stock(
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    store_id uuid not null,

    barcode jsonb not null default '[]'::jsonb,
    name jsonb not null default '[]'::jsonb,

    measurement_unit_id text not null,

    category_ids jsonb not null default '[]'::jsonb,

    sale_prices jsonb not null default '[]'::jsonb,
    return_prices jsonb not null default '[]'::jsonb,
    supply_prices jsonb not null default '[]'::jsonb,

    is_quick_item boolean not null,

    created_at timestamptz not null default now(),
    is_active boolean not null default true
);