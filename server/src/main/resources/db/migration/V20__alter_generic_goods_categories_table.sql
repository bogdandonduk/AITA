create table if not exists generic_goods_categories(
    id uuid primary key,
    type_ids jsonb not null default '[]'::jsonb,
    name jsonb not null default '[]'::jsonb,
    quantity_unit_id text not null,
    image_paths jsonb default '[]'::jsonb
);