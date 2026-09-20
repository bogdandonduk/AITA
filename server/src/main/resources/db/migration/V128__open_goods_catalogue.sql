-- Keep the reusable ODbL catalogue independent of store inventory and community templates.
CREATE TABLE open_goods_catalogue (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    sort_name TEXT NOT NULL,
    search_text TEXT NOT NULL,
    payload TEXT NOT NULL
);
CREATE INDEX open_goods_catalogue_sort ON open_goods_catalogue(sort_name, id);
CREATE TABLE public_catalogue_imports (
    source_key TEXT PRIMARY KEY,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_count INTEGER NOT NULL
);
