ALTER TABLE users ADD COLUMN IF NOT EXISTS app_language VARCHAR(16) NOT NULL DEFAULT 'ru';
ALTER TABLE users ADD COLUMN IF NOT EXISTS app_theme_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS app_size_mode_id BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET app_language = 'ru'
WHERE app_language IS NULL
   OR btrim(app_language) = ''
   OR app_language NOT IN ('system', 'en', 'ru', 'kk');

UPDATE users
SET app_theme_id = 0
WHERE app_theme_id IS NULL OR app_theme_id NOT IN (0, 1);

UPDATE users
SET app_size_mode_id = 0
WHERE app_size_mode_id IS NULL OR app_size_mode_id NOT IN (0, 1);

CREATE OR REPLACE FUNCTION aita_clean_seeded_goods_category_prefix(value text)
RETURNS text
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT btrim(
        regexp_replace(
            coalesce(value, ''),
            '^(Goods[[:space:]]+subcategory|Goods[[:space:]]+(category|categories|section)|Product[[:space:]]+category|Subcategory|Category|Подкатегория[[:space:]]+товаров|Категория[[:space:]]+товаров|Раздел[[:space:]]+товар(ов|а)?|Товарный[[:space:]]+раздел|Тауар[[:space:]]+ішкі[[:space:]]+санаты|Тауар(лар)?[[:space:]]+(санаты|бөлімі)|Товар(лар)?[[:space:]]+(санаты|бөлімі)|Өнім(дер)?[[:space:]]+(санаты|бөлімі)|Санат|Бөлім)[[:space:]]*[:：\-—]?[[:space:]]*',
            '',
            'i'
        ),
        ' :：-—'
    );
$$;

UPDATE generic_goods_categories
SET name = (
    SELECT jsonb_agg(
        CASE
            WHEN elem.value ? 'value'
                THEN jsonb_set(elem.value, '{value}', to_jsonb(aita_clean_seeded_goods_category_prefix(elem.value ->> 'value')), false)
            ELSE elem.value
        END
        ORDER BY elem.ordinality
    )
    FROM jsonb_array_elements(name) WITH ORDINALITY AS elem(value, ordinality)
)
WHERE name IS NOT NULL;

UPDATE generic_goods_categories
SET alias = (
    SELECT jsonb_agg(
        CASE
            WHEN elem.value ? 'value'
                THEN jsonb_set(elem.value, '{value}', to_jsonb(aita_clean_seeded_goods_category_prefix(elem.value ->> 'value')), false)
            ELSE elem.value
        END
        ORDER BY elem.ordinality
    )
    FROM jsonb_array_elements(alias) WITH ORDINALITY AS elem(value, ordinality)
)
WHERE alias IS NOT NULL;

UPDATE generic_goods_categories
SET description = (
    SELECT jsonb_agg(
        CASE
            WHEN elem.value ? 'value'
                THEN jsonb_set(elem.value, '{value}', to_jsonb(aita_clean_seeded_goods_category_prefix(elem.value ->> 'value')), false)
            ELSE elem.value
        END
        ORDER BY elem.ordinality
    )
    FROM jsonb_array_elements(description) WITH ORDINALITY AS elem(value, ordinality)
)
WHERE description IS NOT NULL;

DROP FUNCTION IF EXISTS aita_clean_seeded_goods_category_prefix(text);
