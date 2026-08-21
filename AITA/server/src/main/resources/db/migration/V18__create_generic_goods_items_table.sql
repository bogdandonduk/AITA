create table if not exists generic_goods_categories(
    id uuid primary key,
    type_ids jsonb,
    name jsonb not null,
    quantity_unit jsonb not null,
    image_paths jsonb
);