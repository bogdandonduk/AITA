create table if not exists generic_goods_items(
    id uuid primary key,
    barcode text,
    extra_barcodes text,
    name text not null,
    extra_names text,
    category_id uuid,
    extra_category_ids text,
    suppliers_id uuid,
    extra_supplier_ids text,
    manufacturer_id uuid,
    extra_manufacturer_ids text,
    brand_id uuid
);