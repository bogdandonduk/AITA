create table if not exists stock_batches(
    id uuid primary key,
    goods_item_id uuid not null,

    user_id uuid not null,
    store_id uuid not null,
    supplier_id uuid not null,

    sale_price jsonb not null default '{}'::jsonb,
    return_price jsonb not null default '{}'::jsonb,
    supply_price jsonb not null default '{}'::jsonb,

    supply_time timestamptz not null default now(),
    is_active boolean not null default true
);