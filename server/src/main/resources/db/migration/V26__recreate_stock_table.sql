create table if not exists stock(
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    store_id uuid not null,

    barcode jsonb not null default '[]'::jsonb,
    name jsonb not null default '[]'::jsonb,

    quantity jsonb not null default '[]'::jsonb,

    category_ids jsonb not null default '[]'::jsonb,

    sale_prices jsonb not null default '[]'::jsonb,
    return_prices jsonb not null default '[]'::jsonb,
    supply_prices jsonb not null default '[]'::jsonb,

    is_quick_item boolean not null,

    created_at timestamptz not null default now(),
    is_active boolean not null default true
);

create table if not exists stock_batches(
    id uuid primary key default gen_random_uuid(),
    goods_item_id uuid not null,

    user_id uuid not null,
    store_id uuid not null,
    supplier_id uuid not null,

    sale_price jsonb not null default '{}'::jsonb,
    return_price jsonb not null default '{}'::jsonb,
    supply_price jsonb not null default '{}'::jsonb,

    quantity jsonb not null default '{}'::jsonb,

    supply_time timestamptz not null,
    expiration_time timestamptz not null,

    shelf_queue jsonb not null default '{}'::jsonb,

    created_at timestamptz not null,
    created_by_user_id uuid not null,

    is_active boolean not null default true
);