-- V42: clean category prefixes and expand generic goods categories with localized names/aliases/descriptions.
ALTER TABLE generic_goods_categories ADD COLUMN IF NOT EXISTS alias JSONB NULL;
ALTER TABLE generic_goods_categories ADD COLUMN IF NOT EXISTS description JSONB NULL;

UPDATE generic_goods_categories g
SET name = (
  SELECT jsonb_agg(
    jsonb_set(
      elem,
      '{value}',
      to_jsonb(
        btrim(
          regexp_replace(
            elem->>'value',
            '^(Goods\s+category|Категория\s+товаров|Категория|Товар\s+санаты|Өнім\s+санаты|Санат)\s*:?\s*',
            '',
            'i'
          ),
          ' :-—'
        )
      )
    )
  )
  FROM jsonb_array_elements(g.name) elem
)
WHERE g.name IS NOT NULL;


UPDATE generic_goods_categories g
SET alias = (
  SELECT jsonb_agg(
    jsonb_set(
      elem,
      '{value}',
      to_jsonb(
        btrim(
          regexp_replace(
            elem->>'value',
            '^(Goods\s+category|Категория\s+товаров|Категория|Товар\s+санаты|Өнім\s+санаты|Санат)\s*:?\s*',
            '',
            'i'
          ),
          ' :-—'
        )
      )
    )
  )
  FROM jsonb_array_elements(g.alias) elem
)
WHERE g.alias IS NOT NULL;


UPDATE generic_goods_categories g
SET description = (
  SELECT jsonb_agg(
    jsonb_set(
      elem,
      '{value}',
      to_jsonb(
        btrim(
          regexp_replace(
            elem->>'value',
            '^(Goods\s+category|Категория\s+товаров|Категория|Товар\s+санаты|Өнім\s+санаты|Санат)\s*:?\s*',
            '',
            'i'
          ),
          ' :-—'
        )
      )
    )
  )
  FROM jsonb_array_elements(g.description) elem
)
WHERE g.description IS NOT NULL;

INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('8858fb4c-4cb7-5a83-9f24-b043179840d0'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Food"}, {"language": "en", "value": "Food"}, {"language": "ru", "value": "Еда"}, {"language": "kk", "value": "Азық-түлік"}]'::jsonb, '[{"language": "main", "value": "Food"}, {"language": "en", "value": "Food"}, {"language": "ru", "value": "Еда"}, {"language": "kk", "value": "Азық-түлік"}]'::jsonb, '[{"language": "main", "value": "Food goods group"}, {"language": "en", "value": "Food goods group"}, {"language": "ru", "value": "Раздел товаров: Еда"}, {"language": "kk", "value": "Тауарлар бөлімі: Азық-түлік"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('44cfa052-820c-51c5-b694-ffa4b284bb7d'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Toys"}, {"language": "en", "value": "Toys"}, {"language": "ru", "value": "Игрушки"}, {"language": "kk", "value": "Ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Toys"}, {"language": "en", "value": "Toys"}, {"language": "ru", "value": "Игрушки"}, {"language": "kk", "value": "Ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Toys goods group"}, {"language": "en", "value": "Toys goods group"}, {"language": "ru", "value": "Раздел товаров: Игрушки"}, {"language": "kk", "value": "Тауарлар бөлімі: Ойыншықтар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('21abdf0a-e95e-530a-8395-5c11eb42aac6'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Baby & kids"}, {"language": "en", "value": "Baby & kids"}, {"language": "ru", "value": "Дети и младенцы"}, {"language": "kk", "value": "Балалар мен сәбилер"}]'::jsonb, '[{"language": "main", "value": "Baby & kids"}, {"language": "en", "value": "Baby & kids"}, {"language": "ru", "value": "Дети и младенцы"}, {"language": "kk", "value": "Балалар мен сәбилер"}]'::jsonb, '[{"language": "main", "value": "Baby & kids goods group"}, {"language": "en", "value": "Baby & kids goods group"}, {"language": "ru", "value": "Раздел товаров: Дети и младенцы"}, {"language": "kk", "value": "Тауарлар бөлімі: Балалар мен сәбилер"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('386f446a-74b7-53d1-a785-c27ea7120071'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Clothing"}, {"language": "en", "value": "Clothing"}, {"language": "ru", "value": "Одежда"}, {"language": "kk", "value": "Киім"}]'::jsonb, '[{"language": "main", "value": "Clothing"}, {"language": "en", "value": "Clothing"}, {"language": "ru", "value": "Одежда"}, {"language": "kk", "value": "Киім"}]'::jsonb, '[{"language": "main", "value": "Clothing goods group"}, {"language": "en", "value": "Clothing goods group"}, {"language": "ru", "value": "Раздел товаров: Одежда"}, {"language": "kk", "value": "Тауарлар бөлімі: Киім"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('dea4a6ca-28ab-5a9e-8a43-5678023e161a'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Shoes"}, {"language": "en", "value": "Shoes"}, {"language": "ru", "value": "Обувь"}, {"language": "kk", "value": "Аяқ киім"}]'::jsonb, '[{"language": "main", "value": "Shoes"}, {"language": "en", "value": "Shoes"}, {"language": "ru", "value": "Обувь"}, {"language": "kk", "value": "Аяқ киім"}]'::jsonb, '[{"language": "main", "value": "Shoes goods group"}, {"language": "en", "value": "Shoes goods group"}, {"language": "ru", "value": "Раздел товаров: Обувь"}, {"language": "kk", "value": "Тауарлар бөлімі: Аяқ киім"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('8cbcb139-5e94-58ee-954e-73afc2301ef3'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Electronics"}, {"language": "en", "value": "Electronics"}, {"language": "ru", "value": "Электроника"}, {"language": "kk", "value": "Электроника"}]'::jsonb, '[{"language": "main", "value": "Electronics"}, {"language": "en", "value": "Electronics"}, {"language": "ru", "value": "Электроника"}, {"language": "kk", "value": "Электроника"}]'::jsonb, '[{"language": "main", "value": "Electronics goods group"}, {"language": "en", "value": "Electronics goods group"}, {"language": "ru", "value": "Раздел товаров: Электроника"}, {"language": "kk", "value": "Тауарлар бөлімі: Электроника"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('e932ace1-6f0d-562c-b3d0-530628d9e831'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Home goods"}, {"language": "en", "value": "Home goods"}, {"language": "ru", "value": "Товары для дома"}, {"language": "kk", "value": "Үй тауарлары"}]'::jsonb, '[{"language": "main", "value": "Home goods"}, {"language": "en", "value": "Home goods"}, {"language": "ru", "value": "Товары для дома"}, {"language": "kk", "value": "Үй тауарлары"}]'::jsonb, '[{"language": "main", "value": "Home goods goods group"}, {"language": "en", "value": "Home goods goods group"}, {"language": "ru", "value": "Раздел товаров: Товары для дома"}, {"language": "kk", "value": "Тауарлар бөлімі: Үй тауарлары"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('eb9d2fee-eafa-59aa-8abd-a359a9e8bcc4'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Beauty & health"}, {"language": "en", "value": "Beauty & health"}, {"language": "ru", "value": "Красота и здоровье"}, {"language": "kk", "value": "Сұлулық және денсаулық"}]'::jsonb, '[{"language": "main", "value": "Beauty & health"}, {"language": "en", "value": "Beauty & health"}, {"language": "ru", "value": "Красота и здоровье"}, {"language": "kk", "value": "Сұлулық және денсаулық"}]'::jsonb, '[{"language": "main", "value": "Beauty & health goods group"}, {"language": "en", "value": "Beauty & health goods group"}, {"language": "ru", "value": "Раздел товаров: Красота и здоровье"}, {"language": "kk", "value": "Тауарлар бөлімі: Сұлулық және денсаулық"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('7403d215-4893-5173-bbc0-487f436f6081'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Stationery"}, {"language": "en", "value": "Stationery"}, {"language": "ru", "value": "Канцтовары"}, {"language": "kk", "value": "Кеңсе тауарлары"}]'::jsonb, '[{"language": "main", "value": "Stationery"}, {"language": "en", "value": "Stationery"}, {"language": "ru", "value": "Канцтовары"}, {"language": "kk", "value": "Кеңсе тауарлары"}]'::jsonb, '[{"language": "main", "value": "Stationery goods group"}, {"language": "en", "value": "Stationery goods group"}, {"language": "ru", "value": "Раздел товаров: Канцтовары"}, {"language": "kk", "value": "Тауарлар бөлімі: Кеңсе тауарлары"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('53cf4256-e165-5151-b17d-73e03cdc7e92'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Books & media"}, {"language": "en", "value": "Books & media"}, {"language": "ru", "value": "Книги и медиа"}, {"language": "kk", "value": "Кітаптар және медиа"}]'::jsonb, '[{"language": "main", "value": "Books & media"}, {"language": "en", "value": "Books & media"}, {"language": "ru", "value": "Книги и медиа"}, {"language": "kk", "value": "Кітаптар және медиа"}]'::jsonb, '[{"language": "main", "value": "Books & media goods group"}, {"language": "en", "value": "Books & media goods group"}, {"language": "ru", "value": "Раздел товаров: Книги и медиа"}, {"language": "kk", "value": "Тауарлар бөлімі: Кітаптар және медиа"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('e63d94b5-b1a1-5b4f-b281-27ebc3b02431'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Sports & outdoors"}, {"language": "en", "value": "Sports & outdoors"}, {"language": "ru", "value": "Спорт и отдых"}, {"language": "kk", "value": "Спорт және демалыс"}]'::jsonb, '[{"language": "main", "value": "Sports & outdoors"}, {"language": "en", "value": "Sports & outdoors"}, {"language": "ru", "value": "Спорт и отдых"}, {"language": "kk", "value": "Спорт және демалыс"}]'::jsonb, '[{"language": "main", "value": "Sports & outdoors goods group"}, {"language": "en", "value": "Sports & outdoors goods group"}, {"language": "ru", "value": "Раздел товаров: Спорт и отдых"}, {"language": "kk", "value": "Тауарлар бөлімі: Спорт және демалыс"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('6f81dc41-eade-5c1c-9ac4-92f2bdcee60a'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Pet products"}, {"language": "en", "value": "Pet products"}, {"language": "ru", "value": "Товары для животных"}, {"language": "kk", "value": "Жануарларға арналған тауарлар"}]'::jsonb, '[{"language": "main", "value": "Pet products"}, {"language": "en", "value": "Pet products"}, {"language": "ru", "value": "Товары для животных"}, {"language": "kk", "value": "Жануарларға арналған тауарлар"}]'::jsonb, '[{"language": "main", "value": "Pet products goods group"}, {"language": "en", "value": "Pet products goods group"}, {"language": "ru", "value": "Раздел товаров: Товары для животных"}, {"language": "kk", "value": "Тауарлар бөлімі: Жануарларға арналған тауарлар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('2c5f8f60-69e5-514f-b916-3c54320d8941'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Automotive"}, {"language": "en", "value": "Automotive"}, {"language": "ru", "value": "Автотовары"}, {"language": "kk", "value": "Автотауарлар"}]'::jsonb, '[{"language": "main", "value": "Automotive"}, {"language": "en", "value": "Automotive"}, {"language": "ru", "value": "Автотовары"}, {"language": "kk", "value": "Автотауарлар"}]'::jsonb, '[{"language": "main", "value": "Automotive goods group"}, {"language": "en", "value": "Automotive goods group"}, {"language": "ru", "value": "Раздел товаров: Автотовары"}, {"language": "kk", "value": "Тауарлар бөлімі: Автотауарлар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('2ec60922-66c7-574a-818d-f523f1d7540f'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Accessories"}, {"language": "en", "value": "Accessories"}, {"language": "ru", "value": "Аксессуары"}, {"language": "kk", "value": "Аксессуарлар"}]'::jsonb, '[{"language": "main", "value": "Accessories"}, {"language": "en", "value": "Accessories"}, {"language": "ru", "value": "Аксессуары"}, {"language": "kk", "value": "Аксессуарлар"}]'::jsonb, '[{"language": "main", "value": "Accessories goods group"}, {"language": "en", "value": "Accessories goods group"}, {"language": "ru", "value": "Раздел товаров: Аксессуары"}, {"language": "kk", "value": "Тауарлар бөлімі: Аксессуарлар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b6c5ec8b-c718-5afa-9722-6516274daca9'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Services"}, {"language": "en", "value": "Services"}, {"language": "ru", "value": "Услуги"}, {"language": "kk", "value": "Қызметтер"}]'::jsonb, '[{"language": "main", "value": "Services"}, {"language": "en", "value": "Services"}, {"language": "ru", "value": "Услуги"}, {"language": "kk", "value": "Қызметтер"}]'::jsonb, '[{"language": "main", "value": "Services goods group"}, {"language": "en", "value": "Services goods group"}, {"language": "ru", "value": "Раздел товаров: Услуги"}, {"language": "kk", "value": "Тауарлар бөлімі: Қызметтер"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('042cda5e-276a-5573-869f-f356d77a8a63'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Party & gifts"}, {"language": "en", "value": "Party & gifts"}, {"language": "ru", "value": "Праздники и подарки"}, {"language": "kk", "value": "Мереке және сыйлықтар"}]'::jsonb, '[{"language": "main", "value": "Party & gifts"}, {"language": "en", "value": "Party & gifts"}, {"language": "ru", "value": "Праздники и подарки"}, {"language": "kk", "value": "Мереке және сыйлықтар"}]'::jsonb, '[{"language": "main", "value": "Party & gifts goods group"}, {"language": "en", "value": "Party & gifts goods group"}, {"language": "ru", "value": "Раздел товаров: Праздники и подарки"}, {"language": "kk", "value": "Тауарлар бөлімі: Мереке және сыйлықтар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b526e662-34d5-5a9a-9522-918df027d61a'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Seasonal"}, {"language": "en", "value": "Seasonal"}, {"language": "ru", "value": "Сезонные товары"}, {"language": "kk", "value": "Маусымдық тауарлар"}]'::jsonb, '[{"language": "main", "value": "Seasonal"}, {"language": "en", "value": "Seasonal"}, {"language": "ru", "value": "Сезонные товары"}, {"language": "kk", "value": "Маусымдық тауарлар"}]'::jsonb, '[{"language": "main", "value": "Seasonal goods group"}, {"language": "en", "value": "Seasonal goods group"}, {"language": "ru", "value": "Раздел товаров: Сезонные товары"}, {"language": "kk", "value": "Тауарлар бөлімі: Маусымдық тауарлар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b9ed6967-d66f-5586-9ba0-3de24e732664'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Hobby & craft"}, {"language": "en", "value": "Hobby & craft"}, {"language": "ru", "value": "Хобби и творчество"}, {"language": "kk", "value": "Хобби және шығармашылық"}]'::jsonb, '[{"language": "main", "value": "Hobby & craft"}, {"language": "en", "value": "Hobby & craft"}, {"language": "ru", "value": "Хобби и творчество"}, {"language": "kk", "value": "Хобби және шығармашылық"}]'::jsonb, '[{"language": "main", "value": "Hobby & craft goods group"}, {"language": "en", "value": "Hobby & craft goods group"}, {"language": "ru", "value": "Раздел товаров: Хобби и творчество"}, {"language": "kk", "value": "Тауарлар бөлімі: Хобби және шығармашылық"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('177d0dae-a19b-57b7-ac66-0d65ad9a6056'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Household chemicals"}, {"language": "en", "value": "Household chemicals"}, {"language": "ru", "value": "Бытовая химия"}, {"language": "kk", "value": "Тұрмыстық химия"}]'::jsonb, '[{"language": "main", "value": "Household chemicals"}, {"language": "en", "value": "Household chemicals"}, {"language": "ru", "value": "Бытовая химия"}, {"language": "kk", "value": "Тұрмыстық химия"}]'::jsonb, '[{"language": "main", "value": "Household chemicals goods group"}, {"language": "en", "value": "Household chemicals goods group"}, {"language": "ru", "value": "Раздел товаров: Бытовая химия"}, {"language": "kk", "value": "Тауарлар бөлімі: Тұрмыстық химия"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('4e43b7b1-3f4e-51c0-9c52-da98269286ec'::uuid, '[]'::jsonb, '[{"language": "main", "value": "Souvenirs"}, {"language": "en", "value": "Souvenirs"}, {"language": "ru", "value": "Сувениры"}, {"language": "kk", "value": "Кәдесыйлар"}]'::jsonb, '[{"language": "main", "value": "Souvenirs"}, {"language": "en", "value": "Souvenirs"}, {"language": "ru", "value": "Сувениры"}, {"language": "kk", "value": "Кәдесыйлар"}]'::jsonb, '[{"language": "main", "value": "Souvenirs goods group"}, {"language": "en", "value": "Souvenirs goods group"}, {"language": "ru", "value": "Раздел товаров: Сувениры"}, {"language": "kk", "value": "Тауарлар бөлімі: Кәдесыйлар"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('5db4412f-37d8-5855-9efc-09018705fd35'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Fruits"}, {"language": "en", "value": "Fruits"}, {"language": "ru", "value": "Фрукты"}, {"language": "kk", "value": "Жемістер"}]'::jsonb, '[{"language": "main", "value": "Fruits"}, {"language": "en", "value": "Fruits"}, {"language": "ru", "value": "Фрукты"}, {"language": "kk", "value": "Жемістер"}]'::jsonb, '[{"language": "main", "value": "Fruits in Food"}, {"language": "en", "value": "Fruits in Food"}, {"language": "ru", "value": "Фрукты в разделе Еда"}, {"language": "kk", "value": "Жемістер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('92ec49f2-e10f-5e2d-be18-e44b900f8606'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Vegetables"}, {"language": "en", "value": "Vegetables"}, {"language": "ru", "value": "Овощи"}, {"language": "kk", "value": "Көкөністер"}]'::jsonb, '[{"language": "main", "value": "Vegetables"}, {"language": "en", "value": "Vegetables"}, {"language": "ru", "value": "Овощи"}, {"language": "kk", "value": "Көкөністер"}]'::jsonb, '[{"language": "main", "value": "Vegetables in Food"}, {"language": "en", "value": "Vegetables in Food"}, {"language": "ru", "value": "Овощи в разделе Еда"}, {"language": "kk", "value": "Көкөністер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('fa12c18b-52cd-5356-b145-f6f4f8050880'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Berries"}, {"language": "en", "value": "Berries"}, {"language": "ru", "value": "Ягоды"}, {"language": "kk", "value": "Жидектер"}]'::jsonb, '[{"language": "main", "value": "Berries"}, {"language": "en", "value": "Berries"}, {"language": "ru", "value": "Ягоды"}, {"language": "kk", "value": "Жидектер"}]'::jsonb, '[{"language": "main", "value": "Berries in Food"}, {"language": "en", "value": "Berries in Food"}, {"language": "ru", "value": "Ягоды в разделе Еда"}, {"language": "kk", "value": "Жидектер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('675e729e-fbd6-525c-b801-e8597b15f1b3'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Bakery"}, {"language": "en", "value": "Bakery"}, {"language": "ru", "value": "Выпечка и хлеб"}, {"language": "kk", "value": "Нан және тоқаш"}]'::jsonb, '[{"language": "main", "value": "Bakery"}, {"language": "en", "value": "Bakery"}, {"language": "ru", "value": "Выпечка и хлеб"}, {"language": "kk", "value": "Нан және тоқаш"}]'::jsonb, '[{"language": "main", "value": "Bakery in Food"}, {"language": "en", "value": "Bakery in Food"}, {"language": "ru", "value": "Выпечка и хлеб в разделе Еда"}, {"language": "kk", "value": "Нан және тоқаш — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('6ae77842-c225-5009-93c7-cad4ce2f9beb'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Sweets & snacks"}, {"language": "en", "value": "Sweets & snacks"}, {"language": "ru", "value": "Сладости и снеки"}, {"language": "kk", "value": "Тәттілер мен снектер"}]'::jsonb, '[{"language": "main", "value": "Sweets & snacks"}, {"language": "en", "value": "Sweets & snacks"}, {"language": "ru", "value": "Сладости и снеки"}, {"language": "kk", "value": "Тәттілер мен снектер"}]'::jsonb, '[{"language": "main", "value": "Sweets & snacks in Food"}, {"language": "en", "value": "Sweets & snacks in Food"}, {"language": "ru", "value": "Сладости и снеки в разделе Еда"}, {"language": "kk", "value": "Тәттілер мен снектер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('09308835-8bdb-5a69-8b87-feb2255a7a00'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Beverages"}, {"language": "en", "value": "Beverages"}, {"language": "ru", "value": "Напитки"}, {"language": "kk", "value": "Сусындар"}]'::jsonb, '[{"language": "main", "value": "Beverages"}, {"language": "en", "value": "Beverages"}, {"language": "ru", "value": "Напитки"}, {"language": "kk", "value": "Сусындар"}]'::jsonb, '[{"language": "main", "value": "Beverages in Food"}, {"language": "en", "value": "Beverages in Food"}, {"language": "ru", "value": "Напитки в разделе Еда"}, {"language": "kk", "value": "Сусындар — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('73f68703-dce1-5446-a72f-3344b558acc8'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Dairy"}, {"language": "en", "value": "Dairy"}, {"language": "ru", "value": "Молочные продукты"}, {"language": "kk", "value": "Сүт өнімдері"}]'::jsonb, '[{"language": "main", "value": "Dairy"}, {"language": "en", "value": "Dairy"}, {"language": "ru", "value": "Молочные продукты"}, {"language": "kk", "value": "Сүт өнімдері"}]'::jsonb, '[{"language": "main", "value": "Dairy in Food"}, {"language": "en", "value": "Dairy in Food"}, {"language": "ru", "value": "Молочные продукты в разделе Еда"}, {"language": "kk", "value": "Сүт өнімдері — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('4644af54-08c6-5353-866b-908fea3fac51'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Meat & fish"}, {"language": "en", "value": "Meat & fish"}, {"language": "ru", "value": "Мясо и рыба"}, {"language": "kk", "value": "Ет және балық"}]'::jsonb, '[{"language": "main", "value": "Meat & fish"}, {"language": "en", "value": "Meat & fish"}, {"language": "ru", "value": "Мясо и рыба"}, {"language": "kk", "value": "Ет және балық"}]'::jsonb, '[{"language": "main", "value": "Meat & fish in Food"}, {"language": "en", "value": "Meat & fish in Food"}, {"language": "ru", "value": "Мясо и рыба в разделе Еда"}, {"language": "kk", "value": "Ет және балық — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('d511b3fe-06fc-5628-bd15-8213401f4377'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Frozen food"}, {"language": "en", "value": "Frozen food"}, {"language": "ru", "value": "Замороженные продукты"}, {"language": "kk", "value": "Мұздатылған өнімдер"}]'::jsonb, '[{"language": "main", "value": "Frozen food"}, {"language": "en", "value": "Frozen food"}, {"language": "ru", "value": "Замороженные продукты"}, {"language": "kk", "value": "Мұздатылған өнімдер"}]'::jsonb, '[{"language": "main", "value": "Frozen food in Food"}, {"language": "en", "value": "Frozen food in Food"}, {"language": "ru", "value": "Замороженные продукты в разделе Еда"}, {"language": "kk", "value": "Мұздатылған өнімдер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3a7607fd-45f7-5d8c-87c3-2ebbad3d06ee'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Canned food"}, {"language": "en", "value": "Canned food"}, {"language": "ru", "value": "Консервы"}, {"language": "kk", "value": "Консервілер"}]'::jsonb, '[{"language": "main", "value": "Canned food"}, {"language": "en", "value": "Canned food"}, {"language": "ru", "value": "Консервы"}, {"language": "kk", "value": "Консервілер"}]'::jsonb, '[{"language": "main", "value": "Canned food in Food"}, {"language": "en", "value": "Canned food in Food"}, {"language": "ru", "value": "Консервы в разделе Еда"}, {"language": "kk", "value": "Консервілер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('dd228133-97b8-5962-9632-d950393f27c1'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Baby food"}, {"language": "en", "value": "Baby food"}, {"language": "ru", "value": "Детское питание"}, {"language": "kk", "value": "Балалар тағамы"}]'::jsonb, '[{"language": "main", "value": "Baby food"}, {"language": "en", "value": "Baby food"}, {"language": "ru", "value": "Детское питание"}, {"language": "kk", "value": "Балалар тағамы"}]'::jsonb, '[{"language": "main", "value": "Baby food in Food"}, {"language": "en", "value": "Baby food in Food"}, {"language": "ru", "value": "Детское питание в разделе Еда"}, {"language": "kk", "value": "Балалар тағамы — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('845a1484-5b5d-56cb-b085-7e196061c0ad'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Tea & coffee"}, {"language": "en", "value": "Tea & coffee"}, {"language": "ru", "value": "Чай и кофе"}, {"language": "kk", "value": "Шай және кофе"}]'::jsonb, '[{"language": "main", "value": "Tea & coffee"}, {"language": "en", "value": "Tea & coffee"}, {"language": "ru", "value": "Чай и кофе"}, {"language": "kk", "value": "Шай және кофе"}]'::jsonb, '[{"language": "main", "value": "Tea & coffee in Food"}, {"language": "en", "value": "Tea & coffee in Food"}, {"language": "ru", "value": "Чай и кофе в разделе Еда"}, {"language": "kk", "value": "Шай және кофе — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('24e63035-8344-57c1-9eb2-c0eace4d4147'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Breakfast foods"}, {"language": "en", "value": "Breakfast foods"}, {"language": "ru", "value": "Завтраки"}, {"language": "kk", "value": "Таңғы ас өнімдері"}]'::jsonb, '[{"language": "main", "value": "Breakfast foods"}, {"language": "en", "value": "Breakfast foods"}, {"language": "ru", "value": "Завтраки"}, {"language": "kk", "value": "Таңғы ас өнімдері"}]'::jsonb, '[{"language": "main", "value": "Breakfast foods in Food"}, {"language": "en", "value": "Breakfast foods in Food"}, {"language": "ru", "value": "Завтраки в разделе Еда"}, {"language": "kk", "value": "Таңғы ас өнімдері — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('26ebb99e-7e8d-52ba-a237-941edb639cee'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Pasta & grains"}, {"language": "en", "value": "Pasta & grains"}, {"language": "ru", "value": "Макароны и крупы"}, {"language": "kk", "value": "Макарон және жарма"}]'::jsonb, '[{"language": "main", "value": "Pasta & grains"}, {"language": "en", "value": "Pasta & grains"}, {"language": "ru", "value": "Макароны и крупы"}, {"language": "kk", "value": "Макарон және жарма"}]'::jsonb, '[{"language": "main", "value": "Pasta & grains in Food"}, {"language": "en", "value": "Pasta & grains in Food"}, {"language": "ru", "value": "Макароны и крупы в разделе Еда"}, {"language": "kk", "value": "Макарон және жарма — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('70ba334d-3e8c-577e-ac42-60b9dcf7d8e7'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Sauces & spices"}, {"language": "en", "value": "Sauces & spices"}, {"language": "ru", "value": "Соусы и специи"}, {"language": "kk", "value": "Тұздықтар мен дәмдеуіштер"}]'::jsonb, '[{"language": "main", "value": "Sauces & spices"}, {"language": "en", "value": "Sauces & spices"}, {"language": "ru", "value": "Соусы и специи"}, {"language": "kk", "value": "Тұздықтар мен дәмдеуіштер"}]'::jsonb, '[{"language": "main", "value": "Sauces & spices in Food"}, {"language": "en", "value": "Sauces & spices in Food"}, {"language": "ru", "value": "Соусы и специи в разделе Еда"}, {"language": "kk", "value": "Тұздықтар мен дәмдеуіштер — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f27b10dd-925b-5907-a26d-1f918600cdb9'::uuid, '["8858fb4c-4cb7-5a83-9f24-b043179840d0"]'::jsonb, '[{"language": "main", "value": "Vegan foods"}, {"language": "en", "value": "Vegan foods"}, {"language": "ru", "value": "Веганские продукты"}, {"language": "kk", "value": "Веган өнімдері"}]'::jsonb, '[{"language": "main", "value": "Vegan foods"}, {"language": "en", "value": "Vegan foods"}, {"language": "ru", "value": "Веганские продукты"}, {"language": "kk", "value": "Веган өнімдері"}]'::jsonb, '[{"language": "main", "value": "Vegan foods in Food"}, {"language": "en", "value": "Vegan foods in Food"}, {"language": "ru", "value": "Веганские продукты в разделе Еда"}, {"language": "kk", "value": "Веган өнімдері — Азық-түлік бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f0d7c60b-1acb-532a-bbdd-3b634714e34f'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Plush toys"}, {"language": "en", "value": "Plush toys"}, {"language": "ru", "value": "Мягкие игрушки"}, {"language": "kk", "value": "Жұмсақ ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Plush toys"}, {"language": "en", "value": "Plush toys"}, {"language": "ru", "value": "Мягкие игрушки"}, {"language": "kk", "value": "Жұмсақ ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Plush toys in Toys"}, {"language": "en", "value": "Plush toys in Toys"}, {"language": "ru", "value": "Мягкие игрушки в разделе Игрушки"}, {"language": "kk", "value": "Жұмсақ ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b87f997c-1c67-58a8-90d0-a62824fa950f'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Dolls"}, {"language": "en", "value": "Dolls"}, {"language": "ru", "value": "Куклы"}, {"language": "kk", "value": "Қуыршақтар"}]'::jsonb, '[{"language": "main", "value": "Dolls"}, {"language": "en", "value": "Dolls"}, {"language": "ru", "value": "Куклы"}, {"language": "kk", "value": "Қуыршақтар"}]'::jsonb, '[{"language": "main", "value": "Dolls in Toys"}, {"language": "en", "value": "Dolls in Toys"}, {"language": "ru", "value": "Куклы в разделе Игрушки"}, {"language": "kk", "value": "Қуыршақтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3f1425be-7789-5d14-8033-813ef02dc7a2'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Action figures"}, {"language": "en", "value": "Action figures"}, {"language": "ru", "value": "Фигурки"}, {"language": "kk", "value": "Фигуралар"}]'::jsonb, '[{"language": "main", "value": "Action figures"}, {"language": "en", "value": "Action figures"}, {"language": "ru", "value": "Фигурки"}, {"language": "kk", "value": "Фигуралар"}]'::jsonb, '[{"language": "main", "value": "Action figures in Toys"}, {"language": "en", "value": "Action figures in Toys"}, {"language": "ru", "value": "Фигурки в разделе Игрушки"}, {"language": "kk", "value": "Фигуралар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('e164fe75-893c-5884-82ac-3d2eeb4de32f'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Construction sets"}, {"language": "en", "value": "Construction sets"}, {"language": "ru", "value": "Конструкторы"}, {"language": "kk", "value": "Құрастырмалар"}]'::jsonb, '[{"language": "main", "value": "Construction sets"}, {"language": "en", "value": "Construction sets"}, {"language": "ru", "value": "Конструкторы"}, {"language": "kk", "value": "Құрастырмалар"}]'::jsonb, '[{"language": "main", "value": "Construction sets in Toys"}, {"language": "en", "value": "Construction sets in Toys"}, {"language": "ru", "value": "Конструкторы в разделе Игрушки"}, {"language": "kk", "value": "Құрастырмалар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('32c2cdc0-7e57-57cd-ad43-80df8fa66752'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Board games"}, {"language": "en", "value": "Board games"}, {"language": "ru", "value": "Настольные игры"}, {"language": "kk", "value": "Үстел ойындары"}]'::jsonb, '[{"language": "main", "value": "Board games"}, {"language": "en", "value": "Board games"}, {"language": "ru", "value": "Настольные игры"}, {"language": "kk", "value": "Үстел ойындары"}]'::jsonb, '[{"language": "main", "value": "Board games in Toys"}, {"language": "en", "value": "Board games in Toys"}, {"language": "ru", "value": "Настольные игры в разделе Игрушки"}, {"language": "kk", "value": "Үстел ойындары — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('a73c68ee-e106-521d-b279-70f9296d9173'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Puzzles"}, {"language": "en", "value": "Puzzles"}, {"language": "ru", "value": "Пазлы"}, {"language": "kk", "value": "Пазлдар"}]'::jsonb, '[{"language": "main", "value": "Puzzles"}, {"language": "en", "value": "Puzzles"}, {"language": "ru", "value": "Пазлы"}, {"language": "kk", "value": "Пазлдар"}]'::jsonb, '[{"language": "main", "value": "Puzzles in Toys"}, {"language": "en", "value": "Puzzles in Toys"}, {"language": "ru", "value": "Пазлы в разделе Игрушки"}, {"language": "kk", "value": "Пазлдар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('17885202-2bc1-56eb-bea9-5dbd9712e50c'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Educational toys"}, {"language": "en", "value": "Educational toys"}, {"language": "ru", "value": "Развивающие игрушки"}, {"language": "kk", "value": "Дамытушы ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Educational toys"}, {"language": "en", "value": "Educational toys"}, {"language": "ru", "value": "Развивающие игрушки"}, {"language": "kk", "value": "Дамытушы ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Educational toys in Toys"}, {"language": "en", "value": "Educational toys in Toys"}, {"language": "ru", "value": "Развивающие игрушки в разделе Игрушки"}, {"language": "kk", "value": "Дамытушы ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('34a4d493-b091-50d2-a782-28e496afb84b'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Creative kits"}, {"language": "en", "value": "Creative kits"}, {"language": "ru", "value": "Наборы для творчества"}, {"language": "kk", "value": "Шығармашылық жинақтар"}]'::jsonb, '[{"language": "main", "value": "Creative kits"}, {"language": "en", "value": "Creative kits"}, {"language": "ru", "value": "Наборы для творчества"}, {"language": "kk", "value": "Шығармашылық жинақтар"}]'::jsonb, '[{"language": "main", "value": "Creative kits in Toys"}, {"language": "en", "value": "Creative kits in Toys"}, {"language": "ru", "value": "Наборы для творчества в разделе Игрушки"}, {"language": "kk", "value": "Шығармашылық жинақтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('4f097b76-c7db-5048-bfd4-618050ea72bb'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Remote control toys"}, {"language": "en", "value": "Remote control toys"}, {"language": "ru", "value": "Игрушки на радиоуправлении"}, {"language": "kk", "value": "Қашықтан басқарылатын ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Remote control toys"}, {"language": "en", "value": "Remote control toys"}, {"language": "ru", "value": "Игрушки на радиоуправлении"}, {"language": "kk", "value": "Қашықтан басқарылатын ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Remote control toys in Toys"}, {"language": "en", "value": "Remote control toys in Toys"}, {"language": "ru", "value": "Игрушки на радиоуправлении в разделе Игрушки"}, {"language": "kk", "value": "Қашықтан басқарылатын ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b8c5b1c8-a947-519b-a775-19cd0a9166f4'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Toy cars"}, {"language": "en", "value": "Toy cars"}, {"language": "ru", "value": "Машинки"}, {"language": "kk", "value": "Ойыншық көліктер"}]'::jsonb, '[{"language": "main", "value": "Toy cars"}, {"language": "en", "value": "Toy cars"}, {"language": "ru", "value": "Машинки"}, {"language": "kk", "value": "Ойыншық көліктер"}]'::jsonb, '[{"language": "main", "value": "Toy cars in Toys"}, {"language": "en", "value": "Toy cars in Toys"}, {"language": "ru", "value": "Машинки в разделе Игрушки"}, {"language": "kk", "value": "Ойыншық көліктер — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('daf5cc93-5001-5110-a2f1-765cd0c88c57'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Railways & tracks"}, {"language": "en", "value": "Railways & tracks"}, {"language": "ru", "value": "Железные дороги и треки"}, {"language": "kk", "value": "Теміржолдар мен тректер"}]'::jsonb, '[{"language": "main", "value": "Railways & tracks"}, {"language": "en", "value": "Railways & tracks"}, {"language": "ru", "value": "Железные дороги и треки"}, {"language": "kk", "value": "Теміржолдар мен тректер"}]'::jsonb, '[{"language": "main", "value": "Railways & tracks in Toys"}, {"language": "en", "value": "Railways & tracks in Toys"}, {"language": "ru", "value": "Железные дороги и треки в разделе Игрушки"}, {"language": "kk", "value": "Теміржолдар мен тректер — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0f980e3e-ee9a-51ae-a786-3acaca3ee74a'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Outdoor toys"}, {"language": "en", "value": "Outdoor toys"}, {"language": "ru", "value": "Игрушки для улицы"}, {"language": "kk", "value": "Сыртқы ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Outdoor toys"}, {"language": "en", "value": "Outdoor toys"}, {"language": "ru", "value": "Игрушки для улицы"}, {"language": "kk", "value": "Сыртқы ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Outdoor toys in Toys"}, {"language": "en", "value": "Outdoor toys in Toys"}, {"language": "ru", "value": "Игрушки для улицы в разделе Игрушки"}, {"language": "kk", "value": "Сыртқы ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('222769a5-2e5a-59cd-8787-932cf463c51e'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Role-play toys"}, {"language": "en", "value": "Role-play toys"}, {"language": "ru", "value": "Сюжетно-ролевые игрушки"}, {"language": "kk", "value": "Рөлдік ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Role-play toys"}, {"language": "en", "value": "Role-play toys"}, {"language": "ru", "value": "Сюжетно-ролевые игрушки"}, {"language": "kk", "value": "Рөлдік ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Role-play toys in Toys"}, {"language": "en", "value": "Role-play toys in Toys"}, {"language": "ru", "value": "Сюжетно-ролевые игрушки в разделе Игрушки"}, {"language": "kk", "value": "Рөлдік ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('9c69d353-23bb-5bbe-9dd1-8d096b885329'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Musical toys"}, {"language": "en", "value": "Musical toys"}, {"language": "ru", "value": "Музыкальные игрушки"}, {"language": "kk", "value": "Музыкалық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Musical toys"}, {"language": "en", "value": "Musical toys"}, {"language": "ru", "value": "Музыкальные игрушки"}, {"language": "kk", "value": "Музыкалық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Musical toys in Toys"}, {"language": "en", "value": "Musical toys in Toys"}, {"language": "ru", "value": "Музыкальные игрушки в разделе Игрушки"}, {"language": "kk", "value": "Музыкалық ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('72eb82e7-f21c-50f2-8512-c51491402499'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Bath toys"}, {"language": "en", "value": "Bath toys"}, {"language": "ru", "value": "Игрушки для ванны"}, {"language": "kk", "value": "Ванна ойыншықтары"}]'::jsonb, '[{"language": "main", "value": "Bath toys"}, {"language": "en", "value": "Bath toys"}, {"language": "ru", "value": "Игрушки для ванны"}, {"language": "kk", "value": "Ванна ойыншықтары"}]'::jsonb, '[{"language": "main", "value": "Bath toys in Toys"}, {"language": "en", "value": "Bath toys in Toys"}, {"language": "ru", "value": "Игрушки для ванны в разделе Игрушки"}, {"language": "kk", "value": "Ванна ойыншықтары — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('22159c23-b915-5d7a-a4ab-8b8cbaf0f0e4'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Baby toys"}, {"language": "en", "value": "Baby toys"}, {"language": "ru", "value": "Игрушки для малышей"}, {"language": "kk", "value": "Сәбилер ойыншықтары"}]'::jsonb, '[{"language": "main", "value": "Baby toys"}, {"language": "en", "value": "Baby toys"}, {"language": "ru", "value": "Игрушки для малышей"}, {"language": "kk", "value": "Сәбилер ойыншықтары"}]'::jsonb, '[{"language": "main", "value": "Baby toys in Toys"}, {"language": "en", "value": "Baby toys in Toys"}, {"language": "ru", "value": "Игрушки для малышей в разделе Игрушки"}, {"language": "kk", "value": "Сәбилер ойыншықтары — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3a7384b0-cbf4-5e7a-b561-3f720ed8842f'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Collectibles"}, {"language": "en", "value": "Collectibles"}, {"language": "ru", "value": "Коллекционные игрушки"}, {"language": "kk", "value": "Коллекциялық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Collectibles"}, {"language": "en", "value": "Collectibles"}, {"language": "ru", "value": "Коллекционные игрушки"}, {"language": "kk", "value": "Коллекциялық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Collectibles in Toys"}, {"language": "en", "value": "Collectibles in Toys"}, {"language": "ru", "value": "Коллекционные игрушки в разделе Игрушки"}, {"language": "kk", "value": "Коллекциялық ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0fc4a767-c78f-5296-a83b-7243d4fcab8a'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Toy weapons"}, {"language": "en", "value": "Toy weapons"}, {"language": "ru", "value": "Игрушечное оружие"}, {"language": "kk", "value": "Ойыншық қару"}]'::jsonb, '[{"language": "main", "value": "Toy weapons"}, {"language": "en", "value": "Toy weapons"}, {"language": "ru", "value": "Игрушечное оружие"}, {"language": "kk", "value": "Ойыншық қару"}]'::jsonb, '[{"language": "main", "value": "Toy weapons in Toys"}, {"language": "en", "value": "Toy weapons in Toys"}, {"language": "ru", "value": "Игрушечное оружие в разделе Игрушки"}, {"language": "kk", "value": "Ойыншық қару — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('ed4732b8-8883-54ba-be52-0f2cf59a7264'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Slime & sensory toys"}, {"language": "en", "value": "Slime & sensory toys"}, {"language": "ru", "value": "Слаймы и сенсорные игрушки"}, {"language": "kk", "value": "Слайм және сенсорлық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Slime & sensory toys"}, {"language": "en", "value": "Slime & sensory toys"}, {"language": "ru", "value": "Слаймы и сенсорные игрушки"}, {"language": "kk", "value": "Слайм және сенсорлық ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Slime & sensory toys in Toys"}, {"language": "en", "value": "Slime & sensory toys in Toys"}, {"language": "ru", "value": "Слаймы и сенсорные игрушки в разделе Игрушки"}, {"language": "kk", "value": "Слайм және сенсорлық ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('cc94435e-8c17-505d-9373-45dc07c6818d'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Sand & kinetic sets"}, {"language": "en", "value": "Sand & kinetic sets"}, {"language": "ru", "value": "Песок и кинетические наборы"}, {"language": "kk", "value": "Құм және кинетикалық жинақтар"}]'::jsonb, '[{"language": "main", "value": "Sand & kinetic sets"}, {"language": "en", "value": "Sand & kinetic sets"}, {"language": "ru", "value": "Песок и кинетические наборы"}, {"language": "kk", "value": "Құм және кинетикалық жинақтар"}]'::jsonb, '[{"language": "main", "value": "Sand & kinetic sets in Toys"}, {"language": "en", "value": "Sand & kinetic sets in Toys"}, {"language": "ru", "value": "Песок и кинетические наборы в разделе Игрушки"}, {"language": "kk", "value": "Құм және кинетикалық жинақтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('da5d3c06-cef0-5fe0-a147-e4acd3713c39'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "STEM toys"}, {"language": "en", "value": "STEM toys"}, {"language": "ru", "value": "STEM-игрушки"}, {"language": "kk", "value": "STEM ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "STEM toys"}, {"language": "en", "value": "STEM toys"}, {"language": "ru", "value": "STEM-игрушки"}, {"language": "kk", "value": "STEM ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "STEM toys in Toys"}, {"language": "en", "value": "STEM toys in Toys"}, {"language": "ru", "value": "STEM-игрушки в разделе Игрушки"}, {"language": "kk", "value": "STEM ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('d619cb6c-d1bb-5604-9cd1-ecda40338954'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Blocks"}, {"language": "en", "value": "Blocks"}, {"language": "ru", "value": "Кубики"}, {"language": "kk", "value": "Кубиктер"}]'::jsonb, '[{"language": "main", "value": "Blocks"}, {"language": "en", "value": "Blocks"}, {"language": "ru", "value": "Кубики"}, {"language": "kk", "value": "Кубиктер"}]'::jsonb, '[{"language": "main", "value": "Blocks in Toys"}, {"language": "en", "value": "Blocks in Toys"}, {"language": "ru", "value": "Кубики в разделе Игрушки"}, {"language": "kk", "value": "Кубиктер — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f7ab04e6-6948-5468-942a-6b54472b1126'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Inflatable toys"}, {"language": "en", "value": "Inflatable toys"}, {"language": "ru", "value": "Надувные игрушки"}, {"language": "kk", "value": "Үрлемелі ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Inflatable toys"}, {"language": "en", "value": "Inflatable toys"}, {"language": "ru", "value": "Надувные игрушки"}, {"language": "kk", "value": "Үрлемелі ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Inflatable toys in Toys"}, {"language": "en", "value": "Inflatable toys in Toys"}, {"language": "ru", "value": "Надувные игрушки в разделе Игрушки"}, {"language": "kk", "value": "Үрлемелі ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0f06416e-30f3-597c-ae02-6c8fec02531d'::uuid, '["44cfa052-820c-51c5-b694-ffa4b284bb7d"]'::jsonb, '[{"language": "main", "value": "Ride-on toys"}, {"language": "en", "value": "Ride-on toys"}, {"language": "ru", "value": "Каталки и машинки для катания"}, {"language": "kk", "value": "Мінетін ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Ride-on toys"}, {"language": "en", "value": "Ride-on toys"}, {"language": "ru", "value": "Каталки и машинки для катания"}, {"language": "kk", "value": "Мінетін ойыншықтар"}]'::jsonb, '[{"language": "main", "value": "Ride-on toys in Toys"}, {"language": "en", "value": "Ride-on toys in Toys"}, {"language": "ru", "value": "Каталки и машинки для катания в разделе Игрушки"}, {"language": "kk", "value": "Мінетін ойыншықтар — Ойыншықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('18fff773-87cd-5941-9b2e-8bc04bfda33e'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Diapers"}, {"language": "en", "value": "Diapers"}, {"language": "ru", "value": "Подгузники"}, {"language": "kk", "value": "Жөргектер"}]'::jsonb, '[{"language": "main", "value": "Diapers"}, {"language": "en", "value": "Diapers"}, {"language": "ru", "value": "Подгузники"}, {"language": "kk", "value": "Жөргектер"}]'::jsonb, '[{"language": "main", "value": "Diapers in Baby & kids"}, {"language": "en", "value": "Diapers in Baby & kids"}, {"language": "ru", "value": "Подгузники в разделе Дети и младенцы"}, {"language": "kk", "value": "Жөргектер — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f3249862-b109-5b2c-98aa-56c2044cf691'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Wet wipes"}, {"language": "en", "value": "Wet wipes"}, {"language": "ru", "value": "Влажные салфетки"}, {"language": "kk", "value": "Ылғал майлықтар"}]'::jsonb, '[{"language": "main", "value": "Wet wipes"}, {"language": "en", "value": "Wet wipes"}, {"language": "ru", "value": "Влажные салфетки"}, {"language": "kk", "value": "Ылғал майлықтар"}]'::jsonb, '[{"language": "main", "value": "Wet wipes in Baby & kids"}, {"language": "en", "value": "Wet wipes in Baby & kids"}, {"language": "ru", "value": "Влажные салфетки в разделе Дети и младенцы"}, {"language": "kk", "value": "Ылғал майлықтар — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('01c3b440-f102-5622-8a63-b10969f84632'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Baby care"}, {"language": "en", "value": "Baby care"}, {"language": "ru", "value": "Уход за ребёнком"}, {"language": "kk", "value": "Бала күтімі"}]'::jsonb, '[{"language": "main", "value": "Baby care"}, {"language": "en", "value": "Baby care"}, {"language": "ru", "value": "Уход за ребёнком"}, {"language": "kk", "value": "Бала күтімі"}]'::jsonb, '[{"language": "main", "value": "Baby care in Baby & kids"}, {"language": "en", "value": "Baby care in Baby & kids"}, {"language": "ru", "value": "Уход за ребёнком в разделе Дети и младенцы"}, {"language": "kk", "value": "Бала күтімі — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('ef2f958e-b00e-5135-9324-82896ae193f6'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Feeding bottles"}, {"language": "en", "value": "Feeding bottles"}, {"language": "ru", "value": "Бутылочки для кормления"}, {"language": "kk", "value": "Тамақтандыру бөтелкелері"}]'::jsonb, '[{"language": "main", "value": "Feeding bottles"}, {"language": "en", "value": "Feeding bottles"}, {"language": "ru", "value": "Бутылочки для кормления"}, {"language": "kk", "value": "Тамақтандыру бөтелкелері"}]'::jsonb, '[{"language": "main", "value": "Feeding bottles in Baby & kids"}, {"language": "en", "value": "Feeding bottles in Baby & kids"}, {"language": "ru", "value": "Бутылочки для кормления в разделе Дети и младенцы"}, {"language": "kk", "value": "Тамақтандыру бөтелкелері — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('225b9bef-a743-5ed8-8e63-84523e0e8f13'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Pacifiers"}, {"language": "en", "value": "Pacifiers"}, {"language": "ru", "value": "Пустышки"}, {"language": "kk", "value": "Емізіктер"}]'::jsonb, '[{"language": "main", "value": "Pacifiers"}, {"language": "en", "value": "Pacifiers"}, {"language": "ru", "value": "Пустышки"}, {"language": "kk", "value": "Емізіктер"}]'::jsonb, '[{"language": "main", "value": "Pacifiers in Baby & kids"}, {"language": "en", "value": "Pacifiers in Baby & kids"}, {"language": "ru", "value": "Пустышки в разделе Дети и младенцы"}, {"language": "kk", "value": "Емізіктер — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('75605acd-583f-5ae0-a908-9bdd00458cc3'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Strollers"}, {"language": "en", "value": "Strollers"}, {"language": "ru", "value": "Коляски"}, {"language": "kk", "value": "Арбалар"}]'::jsonb, '[{"language": "main", "value": "Strollers"}, {"language": "en", "value": "Strollers"}, {"language": "ru", "value": "Коляски"}, {"language": "kk", "value": "Арбалар"}]'::jsonb, '[{"language": "main", "value": "Strollers in Baby & kids"}, {"language": "en", "value": "Strollers in Baby & kids"}, {"language": "ru", "value": "Коляски в разделе Дети и младенцы"}, {"language": "kk", "value": "Арбалар — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b85722ef-c020-5413-8606-9a74f9384a15'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Car seats"}, {"language": "en", "value": "Car seats"}, {"language": "ru", "value": "Автокресла"}, {"language": "kk", "value": "Автоорындықтар"}]'::jsonb, '[{"language": "main", "value": "Car seats"}, {"language": "en", "value": "Car seats"}, {"language": "ru", "value": "Автокресла"}, {"language": "kk", "value": "Автоорындықтар"}]'::jsonb, '[{"language": "main", "value": "Car seats in Baby & kids"}, {"language": "en", "value": "Car seats in Baby & kids"}, {"language": "ru", "value": "Автокресла в разделе Дети и младенцы"}, {"language": "kk", "value": "Автоорындықтар — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('1086e9ed-d8fa-5c3a-83bb-59f5efe29859'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "School supplies"}, {"language": "en", "value": "School supplies"}, {"language": "ru", "value": "Школьные принадлежности"}, {"language": "kk", "value": "Мектеп керек-жарақтары"}]'::jsonb, '[{"language": "main", "value": "School supplies"}, {"language": "en", "value": "School supplies"}, {"language": "ru", "value": "Школьные принадлежности"}, {"language": "kk", "value": "Мектеп керек-жарақтары"}]'::jsonb, '[{"language": "main", "value": "School supplies in Baby & kids"}, {"language": "en", "value": "School supplies in Baby & kids"}, {"language": "ru", "value": "Школьные принадлежности в разделе Дети и младенцы"}, {"language": "kk", "value": "Мектеп керек-жарақтары — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f98c6d84-888f-5368-b088-e82a7790dbcb'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Kids hygiene"}, {"language": "en", "value": "Kids hygiene"}, {"language": "ru", "value": "Детская гигиена"}, {"language": "kk", "value": "Балалар гигиенасы"}]'::jsonb, '[{"language": "main", "value": "Kids hygiene"}, {"language": "en", "value": "Kids hygiene"}, {"language": "ru", "value": "Детская гигиена"}, {"language": "kk", "value": "Балалар гигиенасы"}]'::jsonb, '[{"language": "main", "value": "Kids hygiene in Baby & kids"}, {"language": "en", "value": "Kids hygiene in Baby & kids"}, {"language": "ru", "value": "Детская гигиена в разделе Дети и младенцы"}, {"language": "kk", "value": "Балалар гигиенасы — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('d61c3ffc-36d9-56c8-9e68-80f70422b120'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Kids tableware"}, {"language": "en", "value": "Kids tableware"}, {"language": "ru", "value": "Детская посуда"}, {"language": "kk", "value": "Балалар ыдысы"}]'::jsonb, '[{"language": "main", "value": "Kids tableware"}, {"language": "en", "value": "Kids tableware"}, {"language": "ru", "value": "Детская посуда"}, {"language": "kk", "value": "Балалар ыдысы"}]'::jsonb, '[{"language": "main", "value": "Kids tableware in Baby & kids"}, {"language": "en", "value": "Kids tableware in Baby & kids"}, {"language": "ru", "value": "Детская посуда в разделе Дети и младенцы"}, {"language": "kk", "value": "Балалар ыдысы — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('069caf36-50c9-55c4-a11b-cc8214f45860'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Kids room"}, {"language": "en", "value": "Kids room"}, {"language": "ru", "value": "Детская комната"}, {"language": "kk", "value": "Балалар бөлмесі"}]'::jsonb, '[{"language": "main", "value": "Kids room"}, {"language": "en", "value": "Kids room"}, {"language": "ru", "value": "Детская комната"}, {"language": "kk", "value": "Балалар бөлмесі"}]'::jsonb, '[{"language": "main", "value": "Kids room in Baby & kids"}, {"language": "en", "value": "Kids room in Baby & kids"}, {"language": "ru", "value": "Детская комната в разделе Дети и младенцы"}, {"language": "kk", "value": "Балалар бөлмесі — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('9e5513a9-7afd-50ac-92c6-8695650fd4ef'::uuid, '["21abdf0a-e95e-530a-8395-5c11eb42aac6"]'::jsonb, '[{"language": "main", "value": "Baby textiles"}, {"language": "en", "value": "Baby textiles"}, {"language": "ru", "value": "Детский текстиль"}, {"language": "kk", "value": "Балалар тоқыма бұйымдары"}]'::jsonb, '[{"language": "main", "value": "Baby textiles"}, {"language": "en", "value": "Baby textiles"}, {"language": "ru", "value": "Детский текстиль"}, {"language": "kk", "value": "Балалар тоқыма бұйымдары"}]'::jsonb, '[{"language": "main", "value": "Baby textiles in Baby & kids"}, {"language": "en", "value": "Baby textiles in Baby & kids"}, {"language": "ru", "value": "Детский текстиль в разделе Дети и младенцы"}, {"language": "kk", "value": "Балалар тоқыма бұйымдары — Балалар мен сәбилер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('ca0be8f7-a04f-532f-a177-37d36fbe1d8d'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Gift wrapping"}, {"language": "en", "value": "Gift wrapping"}, {"language": "ru", "value": "Подарочная упаковка"}, {"language": "kk", "value": "Сыйлық орау"}]'::jsonb, '[{"language": "main", "value": "Gift wrapping"}, {"language": "en", "value": "Gift wrapping"}, {"language": "ru", "value": "Подарочная упаковка"}, {"language": "kk", "value": "Сыйлық орау"}]'::jsonb, '[{"language": "main", "value": "Gift wrapping in Party & gifts"}, {"language": "en", "value": "Gift wrapping in Party & gifts"}, {"language": "ru", "value": "Подарочная упаковка в разделе Праздники и подарки"}, {"language": "kk", "value": "Сыйлық орау — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3378a4d6-f102-5c24-b493-1ac6652f1034'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Balloons"}, {"language": "en", "value": "Balloons"}, {"language": "ru", "value": "Воздушные шары"}, {"language": "kk", "value": "Шарлар"}]'::jsonb, '[{"language": "main", "value": "Balloons"}, {"language": "en", "value": "Balloons"}, {"language": "ru", "value": "Воздушные шары"}, {"language": "kk", "value": "Шарлар"}]'::jsonb, '[{"language": "main", "value": "Balloons in Party & gifts"}, {"language": "en", "value": "Balloons in Party & gifts"}, {"language": "ru", "value": "Воздушные шары в разделе Праздники и подарки"}, {"language": "kk", "value": "Шарлар — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('8943f790-768c-5831-82a4-13ecf7388fe0'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Greeting cards"}, {"language": "en", "value": "Greeting cards"}, {"language": "ru", "value": "Открытки"}, {"language": "kk", "value": "Ашықхаттар"}]'::jsonb, '[{"language": "main", "value": "Greeting cards"}, {"language": "en", "value": "Greeting cards"}, {"language": "ru", "value": "Открытки"}, {"language": "kk", "value": "Ашықхаттар"}]'::jsonb, '[{"language": "main", "value": "Greeting cards in Party & gifts"}, {"language": "en", "value": "Greeting cards in Party & gifts"}, {"language": "ru", "value": "Открытки в разделе Праздники и подарки"}, {"language": "kk", "value": "Ашықхаттар — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('7f164bc5-c6c1-5292-ac3f-577a765f5d73'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Party decorations"}, {"language": "en", "value": "Party decorations"}, {"language": "ru", "value": "Праздничный декор"}, {"language": "kk", "value": "Мерекелік безендіру"}]'::jsonb, '[{"language": "main", "value": "Party decorations"}, {"language": "en", "value": "Party decorations"}, {"language": "ru", "value": "Праздничный декор"}, {"language": "kk", "value": "Мерекелік безендіру"}]'::jsonb, '[{"language": "main", "value": "Party decorations in Party & gifts"}, {"language": "en", "value": "Party decorations in Party & gifts"}, {"language": "ru", "value": "Праздничный декор в разделе Праздники и подарки"}, {"language": "kk", "value": "Мерекелік безендіру — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('24cea1bd-a5f1-5345-b176-4367773fe5f1'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Birthday candles"}, {"language": "en", "value": "Birthday candles"}, {"language": "ru", "value": "Свечи для торта"}, {"language": "kk", "value": "Торт шамдары"}]'::jsonb, '[{"language": "main", "value": "Birthday candles"}, {"language": "en", "value": "Birthday candles"}, {"language": "ru", "value": "Свечи для торта"}, {"language": "kk", "value": "Торт шамдары"}]'::jsonb, '[{"language": "main", "value": "Birthday candles in Party & gifts"}, {"language": "en", "value": "Birthday candles in Party & gifts"}, {"language": "ru", "value": "Свечи для торта в разделе Праздники и подарки"}, {"language": "kk", "value": "Торт шамдары — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b0bf8c46-5df1-51fe-b565-74e20a52d2a4'::uuid, '["042cda5e-276a-5573-869f-f356d77a8a63"]'::jsonb, '[{"language": "main", "value": "Gift sets"}, {"language": "en", "value": "Gift sets"}, {"language": "ru", "value": "Подарочные наборы"}, {"language": "kk", "value": "Сыйлық жинақтары"}]'::jsonb, '[{"language": "main", "value": "Gift sets"}, {"language": "en", "value": "Gift sets"}, {"language": "ru", "value": "Подарочные наборы"}, {"language": "kk", "value": "Сыйлық жинақтары"}]'::jsonb, '[{"language": "main", "value": "Gift sets in Party & gifts"}, {"language": "en", "value": "Gift sets in Party & gifts"}, {"language": "ru", "value": "Подарочные наборы в разделе Праздники и подарки"}, {"language": "kk", "value": "Сыйлық жинақтары — Мереке және сыйлықтар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('e12aad48-1569-51af-9e4c-c7e8261fa40f'::uuid, '["b526e662-34d5-5a9a-9522-918df027d61a"]'::jsonb, '[{"language": "main", "value": "New Year goods"}, {"language": "en", "value": "New Year goods"}, {"language": "ru", "value": "Новогодние товары"}, {"language": "kk", "value": "Жаңа жыл тауарлары"}]'::jsonb, '[{"language": "main", "value": "New Year goods"}, {"language": "en", "value": "New Year goods"}, {"language": "ru", "value": "Новогодние товары"}, {"language": "kk", "value": "Жаңа жыл тауарлары"}]'::jsonb, '[{"language": "main", "value": "New Year goods in Seasonal"}, {"language": "en", "value": "New Year goods in Seasonal"}, {"language": "ru", "value": "Новогодние товары в разделе Сезонные товары"}, {"language": "kk", "value": "Жаңа жыл тауарлары — Маусымдық тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('54fed16d-5039-586b-91ef-e822d0476c8b'::uuid, '["b526e662-34d5-5a9a-9522-918df027d61a"]'::jsonb, '[{"language": "main", "value": "Summer goods"}, {"language": "en", "value": "Summer goods"}, {"language": "ru", "value": "Летние товары"}, {"language": "kk", "value": "Жазғы тауарлар"}]'::jsonb, '[{"language": "main", "value": "Summer goods"}, {"language": "en", "value": "Summer goods"}, {"language": "ru", "value": "Летние товары"}, {"language": "kk", "value": "Жазғы тауарлар"}]'::jsonb, '[{"language": "main", "value": "Summer goods in Seasonal"}, {"language": "en", "value": "Summer goods in Seasonal"}, {"language": "ru", "value": "Летние товары в разделе Сезонные товары"}, {"language": "kk", "value": "Жазғы тауарлар — Маусымдық тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('50991c1a-7df2-5a90-af12-2eb126f4bd91'::uuid, '["b526e662-34d5-5a9a-9522-918df027d61a"]'::jsonb, '[{"language": "main", "value": "Winter goods"}, {"language": "en", "value": "Winter goods"}, {"language": "ru", "value": "Зимние товары"}, {"language": "kk", "value": "Қысқы тауарлар"}]'::jsonb, '[{"language": "main", "value": "Winter goods"}, {"language": "en", "value": "Winter goods"}, {"language": "ru", "value": "Зимние товары"}, {"language": "kk", "value": "Қысқы тауарлар"}]'::jsonb, '[{"language": "main", "value": "Winter goods in Seasonal"}, {"language": "en", "value": "Winter goods in Seasonal"}, {"language": "ru", "value": "Зимние товары в разделе Сезонные товары"}, {"language": "kk", "value": "Қысқы тауарлар — Маусымдық тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('428f1bb3-b85e-5b61-a370-b88aeea3467a'::uuid, '["b526e662-34d5-5a9a-9522-918df027d61a"]'::jsonb, '[{"language": "main", "value": "School season"}, {"language": "en", "value": "School season"}, {"language": "ru", "value": "Школьный сезон"}, {"language": "kk", "value": "Мектеп маусымы"}]'::jsonb, '[{"language": "main", "value": "School season"}, {"language": "en", "value": "School season"}, {"language": "ru", "value": "Школьный сезон"}, {"language": "kk", "value": "Мектеп маусымы"}]'::jsonb, '[{"language": "main", "value": "School season in Seasonal"}, {"language": "en", "value": "School season in Seasonal"}, {"language": "ru", "value": "Школьный сезон в разделе Сезонные товары"}, {"language": "kk", "value": "Мектеп маусымы — Маусымдық тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b50fe6c1-6ed6-5773-bc37-62ac73c71cd1'::uuid, '["b526e662-34d5-5a9a-9522-918df027d61a"]'::jsonb, '[{"language": "main", "value": "Halloween goods"}, {"language": "en", "value": "Halloween goods"}, {"language": "ru", "value": "Товары для Хэллоуина"}, {"language": "kk", "value": "Хэллоуин тауарлары"}]'::jsonb, '[{"language": "main", "value": "Halloween goods"}, {"language": "en", "value": "Halloween goods"}, {"language": "ru", "value": "Товары для Хэллоуина"}, {"language": "kk", "value": "Хэллоуин тауарлары"}]'::jsonb, '[{"language": "main", "value": "Halloween goods in Seasonal"}, {"language": "en", "value": "Halloween goods in Seasonal"}, {"language": "ru", "value": "Товары для Хэллоуина в разделе Сезонные товары"}, {"language": "kk", "value": "Хэллоуин тауарлары — Маусымдық тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('5c9cbbd3-4a1f-5149-b635-fd37961e86db'::uuid, '["b9ed6967-d66f-5586-9ba0-3de24e732664"]'::jsonb, '[{"language": "main", "value": "Drawing & painting"}, {"language": "en", "value": "Drawing & painting"}, {"language": "ru", "value": "Рисование и живопись"}, {"language": "kk", "value": "Сурет салу"}]'::jsonb, '[{"language": "main", "value": "Drawing & painting"}, {"language": "en", "value": "Drawing & painting"}, {"language": "ru", "value": "Рисование и живопись"}, {"language": "kk", "value": "Сурет салу"}]'::jsonb, '[{"language": "main", "value": "Drawing & painting in Hobby & craft"}, {"language": "en", "value": "Drawing & painting in Hobby & craft"}, {"language": "ru", "value": "Рисование и живопись в разделе Хобби и творчество"}, {"language": "kk", "value": "Сурет салу — Хобби және шығармашылық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('7aa1d172-abeb-5373-84ac-15ffe48e45ec'::uuid, '["b9ed6967-d66f-5586-9ba0-3de24e732664"]'::jsonb, '[{"language": "main", "value": "Modeling clay"}, {"language": "en", "value": "Modeling clay"}, {"language": "ru", "value": "Пластилин и лепка"}, {"language": "kk", "value": "Ермексаз және мүсіндеу"}]'::jsonb, '[{"language": "main", "value": "Modeling clay"}, {"language": "en", "value": "Modeling clay"}, {"language": "ru", "value": "Пластилин и лепка"}, {"language": "kk", "value": "Ермексаз және мүсіндеу"}]'::jsonb, '[{"language": "main", "value": "Modeling clay in Hobby & craft"}, {"language": "en", "value": "Modeling clay in Hobby & craft"}, {"language": "ru", "value": "Пластилин и лепка в разделе Хобби и творчество"}, {"language": "kk", "value": "Ермексаз және мүсіндеу — Хобби және шығармашылық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('c1811ad2-3742-55ae-82a1-c327744408f8'::uuid, '["b9ed6967-d66f-5586-9ba0-3de24e732664"]'::jsonb, '[{"language": "main", "value": "Beads & jewelry kits"}, {"language": "en", "value": "Beads & jewelry kits"}, {"language": "ru", "value": "Бисер и украшения"}, {"language": "kk", "value": "Моншақ және әшекей жинақтары"}]'::jsonb, '[{"language": "main", "value": "Beads & jewelry kits"}, {"language": "en", "value": "Beads & jewelry kits"}, {"language": "ru", "value": "Бисер и украшения"}, {"language": "kk", "value": "Моншақ және әшекей жинақтары"}]'::jsonb, '[{"language": "main", "value": "Beads & jewelry kits in Hobby & craft"}, {"language": "en", "value": "Beads & jewelry kits in Hobby & craft"}, {"language": "ru", "value": "Бисер и украшения в разделе Хобби и творчество"}, {"language": "kk", "value": "Моншақ және әшекей жинақтары — Хобби және шығармашылық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('9b34d359-b551-5a20-b609-1e679a64c0f8'::uuid, '["b9ed6967-d66f-5586-9ba0-3de24e732664"]'::jsonb, '[{"language": "main", "value": "Sewing & knitting"}, {"language": "en", "value": "Sewing & knitting"}, {"language": "ru", "value": "Шитьё и вязание"}, {"language": "kk", "value": "Тігін және тоқу"}]'::jsonb, '[{"language": "main", "value": "Sewing & knitting"}, {"language": "en", "value": "Sewing & knitting"}, {"language": "ru", "value": "Шитьё и вязание"}, {"language": "kk", "value": "Тігін және тоқу"}]'::jsonb, '[{"language": "main", "value": "Sewing & knitting in Hobby & craft"}, {"language": "en", "value": "Sewing & knitting in Hobby & craft"}, {"language": "ru", "value": "Шитьё и вязание в разделе Хобби и творчество"}, {"language": "kk", "value": "Тігін және тоқу — Хобби және шығармашылық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('878db855-8d77-5849-9e79-fa346b65dcbb'::uuid, '["b9ed6967-d66f-5586-9ba0-3de24e732664"]'::jsonb, '[{"language": "main", "value": "DIY kits"}, {"language": "en", "value": "DIY kits"}, {"language": "ru", "value": "DIY-наборы"}, {"language": "kk", "value": "DIY жинақтар"}]'::jsonb, '[{"language": "main", "value": "DIY kits"}, {"language": "en", "value": "DIY kits"}, {"language": "ru", "value": "DIY-наборы"}, {"language": "kk", "value": "DIY жинақтар"}]'::jsonb, '[{"language": "main", "value": "DIY kits in Hobby & craft"}, {"language": "en", "value": "DIY kits in Hobby & craft"}, {"language": "ru", "value": "DIY-наборы в разделе Хобби и творчество"}, {"language": "kk", "value": "DIY жинақтар — Хобби және шығармашылық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('081702a4-5bb5-5945-bf21-7183ab084c4d'::uuid, '["4e43b7b1-3f4e-51c0-9c52-da98269286ec"]'::jsonb, '[{"language": "main", "value": "Magnets"}, {"language": "en", "value": "Magnets"}, {"language": "ru", "value": "Магниты"}, {"language": "kk", "value": "Магниттер"}]'::jsonb, '[{"language": "main", "value": "Magnets"}, {"language": "en", "value": "Magnets"}, {"language": "ru", "value": "Магниты"}, {"language": "kk", "value": "Магниттер"}]'::jsonb, '[{"language": "main", "value": "Magnets in Souvenirs"}, {"language": "en", "value": "Magnets in Souvenirs"}, {"language": "ru", "value": "Магниты в разделе Сувениры"}, {"language": "kk", "value": "Магниттер — Кәдесыйлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0091ec42-8e37-5594-923e-44ebb496c463'::uuid, '["4e43b7b1-3f4e-51c0-9c52-da98269286ec"]'::jsonb, '[{"language": "main", "value": "Keychains"}, {"language": "en", "value": "Keychains"}, {"language": "ru", "value": "Брелоки"}, {"language": "kk", "value": "Брелоктар"}]'::jsonb, '[{"language": "main", "value": "Keychains"}, {"language": "en", "value": "Keychains"}, {"language": "ru", "value": "Брелоки"}, {"language": "kk", "value": "Брелоктар"}]'::jsonb, '[{"language": "main", "value": "Keychains in Souvenirs"}, {"language": "en", "value": "Keychains in Souvenirs"}, {"language": "ru", "value": "Брелоки в разделе Сувениры"}, {"language": "kk", "value": "Брелоктар — Кәдесыйлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('1a4d8a08-e683-5a8b-a6cf-fda607483d21'::uuid, '["4e43b7b1-3f4e-51c0-9c52-da98269286ec"]'::jsonb, '[{"language": "main", "value": "Figurines"}, {"language": "en", "value": "Figurines"}, {"language": "ru", "value": "Сувенирные фигурки"}, {"language": "kk", "value": "Кәдесый мүсіншелер"}]'::jsonb, '[{"language": "main", "value": "Figurines"}, {"language": "en", "value": "Figurines"}, {"language": "ru", "value": "Сувенирные фигурки"}, {"language": "kk", "value": "Кәдесый мүсіншелер"}]'::jsonb, '[{"language": "main", "value": "Figurines in Souvenirs"}, {"language": "en", "value": "Figurines in Souvenirs"}, {"language": "ru", "value": "Сувенирные фигурки в разделе Сувениры"}, {"language": "kk", "value": "Кәдесый мүсіншелер — Кәдесыйлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('7f7729e7-757c-5bb9-951c-d29e54774510'::uuid, '["4e43b7b1-3f4e-51c0-9c52-da98269286ec"]'::jsonb, '[{"language": "main", "value": "Local souvenirs"}, {"language": "en", "value": "Local souvenirs"}, {"language": "ru", "value": "Местные сувениры"}, {"language": "kk", "value": "Жергілікті кәдесыйлар"}]'::jsonb, '[{"language": "main", "value": "Local souvenirs"}, {"language": "en", "value": "Local souvenirs"}, {"language": "ru", "value": "Местные сувениры"}, {"language": "kk", "value": "Жергілікті кәдесыйлар"}]'::jsonb, '[{"language": "main", "value": "Local souvenirs in Souvenirs"}, {"language": "en", "value": "Local souvenirs in Souvenirs"}, {"language": "ru", "value": "Местные сувениры в разделе Сувениры"}, {"language": "kk", "value": "Жергілікті кәдесыйлар — Кәдесыйлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('c3b1b30e-be56-523b-a23f-595722b9c0cd'::uuid, '["386f446a-74b7-53d1-a785-c27ea7120071"]'::jsonb, '[{"language": "main", "value": "Kids clothing"}, {"language": "en", "value": "Kids clothing"}, {"language": "ru", "value": "Детская одежда"}, {"language": "kk", "value": "Балалар киімі"}]'::jsonb, '[{"language": "main", "value": "Kids clothing"}, {"language": "en", "value": "Kids clothing"}, {"language": "ru", "value": "Детская одежда"}, {"language": "kk", "value": "Балалар киімі"}]'::jsonb, '[{"language": "main", "value": "Kids clothing in Clothing"}, {"language": "en", "value": "Kids clothing in Clothing"}, {"language": "ru", "value": "Детская одежда в разделе Одежда"}, {"language": "kk", "value": "Балалар киімі — Киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('cc0f7a01-67d5-5654-a620-95ec317d888f'::uuid, '["386f446a-74b7-53d1-a785-c27ea7120071"]'::jsonb, '[{"language": "main", "value": "Baby clothing"}, {"language": "en", "value": "Baby clothing"}, {"language": "ru", "value": "Одежда для младенцев"}, {"language": "kk", "value": "Сәбилер киімі"}]'::jsonb, '[{"language": "main", "value": "Baby clothing"}, {"language": "en", "value": "Baby clothing"}, {"language": "ru", "value": "Одежда для младенцев"}, {"language": "kk", "value": "Сәбилер киімі"}]'::jsonb, '[{"language": "main", "value": "Baby clothing in Clothing"}, {"language": "en", "value": "Baby clothing in Clothing"}, {"language": "ru", "value": "Одежда для младенцев в разделе Одежда"}, {"language": "kk", "value": "Сәбилер киімі — Киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('9df4f0ec-9607-5795-a5f6-9dfe8ee5f09e'::uuid, '["386f446a-74b7-53d1-a785-c27ea7120071"]'::jsonb, '[{"language": "main", "value": "Underwear"}, {"language": "en", "value": "Underwear"}, {"language": "ru", "value": "Нижнее бельё"}, {"language": "kk", "value": "Іш киім"}]'::jsonb, '[{"language": "main", "value": "Underwear"}, {"language": "en", "value": "Underwear"}, {"language": "ru", "value": "Нижнее бельё"}, {"language": "kk", "value": "Іш киім"}]'::jsonb, '[{"language": "main", "value": "Underwear in Clothing"}, {"language": "en", "value": "Underwear in Clothing"}, {"language": "ru", "value": "Нижнее бельё в разделе Одежда"}, {"language": "kk", "value": "Іш киім — Киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('e772f6a9-d99b-5b45-aa28-89b078714206'::uuid, '["386f446a-74b7-53d1-a785-c27ea7120071"]'::jsonb, '[{"language": "main", "value": "Outerwear"}, {"language": "en", "value": "Outerwear"}, {"language": "ru", "value": "Верхняя одежда"}, {"language": "kk", "value": "Сырт киім"}]'::jsonb, '[{"language": "main", "value": "Outerwear"}, {"language": "en", "value": "Outerwear"}, {"language": "ru", "value": "Верхняя одежда"}, {"language": "kk", "value": "Сырт киім"}]'::jsonb, '[{"language": "main", "value": "Outerwear in Clothing"}, {"language": "en", "value": "Outerwear in Clothing"}, {"language": "ru", "value": "Верхняя одежда в разделе Одежда"}, {"language": "kk", "value": "Сырт киім — Киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3d84cc92-67f9-527f-baf2-c5f4fa7d03d2'::uuid, '["386f446a-74b7-53d1-a785-c27ea7120071"]'::jsonb, '[{"language": "main", "value": "Socks & tights"}, {"language": "en", "value": "Socks & tights"}, {"language": "ru", "value": "Носки и колготки"}, {"language": "kk", "value": "Шұлықтар мен колготки"}]'::jsonb, '[{"language": "main", "value": "Socks & tights"}, {"language": "en", "value": "Socks & tights"}, {"language": "ru", "value": "Носки и колготки"}, {"language": "kk", "value": "Шұлықтар мен колготки"}]'::jsonb, '[{"language": "main", "value": "Socks & tights in Clothing"}, {"language": "en", "value": "Socks & tights in Clothing"}, {"language": "ru", "value": "Носки и колготки в разделе Одежда"}, {"language": "kk", "value": "Шұлықтар мен колготки — Киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('94eec882-c2ab-5b6f-920c-384def0ff5fd'::uuid, '["dea4a6ca-28ab-5a9e-8a43-5678023e161a"]'::jsonb, '[{"language": "main", "value": "Kids shoes"}, {"language": "en", "value": "Kids shoes"}, {"language": "ru", "value": "Детская обувь"}, {"language": "kk", "value": "Балалар аяқ киімі"}]'::jsonb, '[{"language": "main", "value": "Kids shoes"}, {"language": "en", "value": "Kids shoes"}, {"language": "ru", "value": "Детская обувь"}, {"language": "kk", "value": "Балалар аяқ киімі"}]'::jsonb, '[{"language": "main", "value": "Kids shoes in Shoes"}, {"language": "en", "value": "Kids shoes in Shoes"}, {"language": "ru", "value": "Детская обувь в разделе Обувь"}, {"language": "kk", "value": "Балалар аяқ киімі — Аяқ киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('be5d8180-4d59-59b2-8bc2-4f383bbc2b7a'::uuid, '["dea4a6ca-28ab-5a9e-8a43-5678023e161a"]'::jsonb, '[{"language": "main", "value": "Baby shoes"}, {"language": "en", "value": "Baby shoes"}, {"language": "ru", "value": "Обувь для малышей"}, {"language": "kk", "value": "Сәбилер аяқ киімі"}]'::jsonb, '[{"language": "main", "value": "Baby shoes"}, {"language": "en", "value": "Baby shoes"}, {"language": "ru", "value": "Обувь для малышей"}, {"language": "kk", "value": "Сәбилер аяқ киімі"}]'::jsonb, '[{"language": "main", "value": "Baby shoes in Shoes"}, {"language": "en", "value": "Baby shoes in Shoes"}, {"language": "ru", "value": "Обувь для малышей в разделе Обувь"}, {"language": "kk", "value": "Сәбилер аяқ киімі — Аяқ киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('017969d1-cc9b-547c-addb-d2f75cf5712c'::uuid, '["dea4a6ca-28ab-5a9e-8a43-5678023e161a"]'::jsonb, '[{"language": "main", "value": "Sneakers"}, {"language": "en", "value": "Sneakers"}, {"language": "ru", "value": "Кроссовки"}, {"language": "kk", "value": "Кроссовкалар"}]'::jsonb, '[{"language": "main", "value": "Sneakers"}, {"language": "en", "value": "Sneakers"}, {"language": "ru", "value": "Кроссовки"}, {"language": "kk", "value": "Кроссовкалар"}]'::jsonb, '[{"language": "main", "value": "Sneakers in Shoes"}, {"language": "en", "value": "Sneakers in Shoes"}, {"language": "ru", "value": "Кроссовки в разделе Обувь"}, {"language": "kk", "value": "Кроссовкалар — Аяқ киім бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('f25ba316-545e-5a42-a89e-2093d384e414'::uuid, '["8cbcb139-5e94-58ee-954e-73afc2301ef3"]'::jsonb, '[{"language": "main", "value": "Batteries"}, {"language": "en", "value": "Batteries"}, {"language": "ru", "value": "Батарейки"}, {"language": "kk", "value": "Батареялар"}]'::jsonb, '[{"language": "main", "value": "Batteries"}, {"language": "en", "value": "Batteries"}, {"language": "ru", "value": "Батарейки"}, {"language": "kk", "value": "Батареялар"}]'::jsonb, '[{"language": "main", "value": "Batteries in Electronics"}, {"language": "en", "value": "Batteries in Electronics"}, {"language": "ru", "value": "Батарейки в разделе Электроника"}, {"language": "kk", "value": "Батареялар — Электроника бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0a552b35-ca3d-5363-913f-84cd61303d42'::uuid, '["8cbcb139-5e94-58ee-954e-73afc2301ef3"]'::jsonb, '[{"language": "main", "value": "Chargers"}, {"language": "en", "value": "Chargers"}, {"language": "ru", "value": "Зарядные устройства"}, {"language": "kk", "value": "Зарядтағыштар"}]'::jsonb, '[{"language": "main", "value": "Chargers"}, {"language": "en", "value": "Chargers"}, {"language": "ru", "value": "Зарядные устройства"}, {"language": "kk", "value": "Зарядтағыштар"}]'::jsonb, '[{"language": "main", "value": "Chargers in Electronics"}, {"language": "en", "value": "Chargers in Electronics"}, {"language": "ru", "value": "Зарядные устройства в разделе Электроника"}, {"language": "kk", "value": "Зарядтағыштар — Электроника бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('5f711213-bcd4-5e26-8340-3d18d80b4bb7'::uuid, '["8cbcb139-5e94-58ee-954e-73afc2301ef3"]'::jsonb, '[{"language": "main", "value": "Cables"}, {"language": "en", "value": "Cables"}, {"language": "ru", "value": "Кабели"}, {"language": "kk", "value": "Кабельдер"}]'::jsonb, '[{"language": "main", "value": "Cables"}, {"language": "en", "value": "Cables"}, {"language": "ru", "value": "Кабели"}, {"language": "kk", "value": "Кабельдер"}]'::jsonb, '[{"language": "main", "value": "Cables in Electronics"}, {"language": "en", "value": "Cables in Electronics"}, {"language": "ru", "value": "Кабели в разделе Электроника"}, {"language": "kk", "value": "Кабельдер — Электроника бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('69dfe18f-7265-5ccd-b76b-779c8b2e9829'::uuid, '["8cbcb139-5e94-58ee-954e-73afc2301ef3"]'::jsonb, '[{"language": "main", "value": "Small electronics"}, {"language": "en", "value": "Small electronics"}, {"language": "ru", "value": "Мелкая электроника"}, {"language": "kk", "value": "Ұсақ электроника"}]'::jsonb, '[{"language": "main", "value": "Small electronics"}, {"language": "en", "value": "Small electronics"}, {"language": "ru", "value": "Мелкая электроника"}, {"language": "kk", "value": "Ұсақ электроника"}]'::jsonb, '[{"language": "main", "value": "Small electronics in Electronics"}, {"language": "en", "value": "Small electronics in Electronics"}, {"language": "ru", "value": "Мелкая электроника в разделе Электроника"}, {"language": "kk", "value": "Ұсақ электроника — Электроника бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('34fc022b-1c0a-5dc6-9845-54493ee80932'::uuid, '["e932ace1-6f0d-562c-b3d0-530628d9e831"]'::jsonb, '[{"language": "main", "value": "Kitchenware"}, {"language": "en", "value": "Kitchenware"}, {"language": "ru", "value": "Кухонные товары"}, {"language": "kk", "value": "Ас үй тауарлары"}]'::jsonb, '[{"language": "main", "value": "Kitchenware"}, {"language": "en", "value": "Kitchenware"}, {"language": "ru", "value": "Кухонные товары"}, {"language": "kk", "value": "Ас үй тауарлары"}]'::jsonb, '[{"language": "main", "value": "Kitchenware in Home goods"}, {"language": "en", "value": "Kitchenware in Home goods"}, {"language": "ru", "value": "Кухонные товары в разделе Товары для дома"}, {"language": "kk", "value": "Ас үй тауарлары — Үй тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3cfc9d39-da44-59d6-9c50-06935674bb66'::uuid, '["e932ace1-6f0d-562c-b3d0-530628d9e831"]'::jsonb, '[{"language": "main", "value": "Textiles"}, {"language": "en", "value": "Textiles"}, {"language": "ru", "value": "Текстиль"}, {"language": "kk", "value": "Тоқыма"}]'::jsonb, '[{"language": "main", "value": "Textiles"}, {"language": "en", "value": "Textiles"}, {"language": "ru", "value": "Текстиль"}, {"language": "kk", "value": "Тоқыма"}]'::jsonb, '[{"language": "main", "value": "Textiles in Home goods"}, {"language": "en", "value": "Textiles in Home goods"}, {"language": "ru", "value": "Текстиль в разделе Товары для дома"}, {"language": "kk", "value": "Тоқыма — Үй тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('c69372d6-bdfc-57b6-a800-d21f5f562964'::uuid, '["e932ace1-6f0d-562c-b3d0-530628d9e831"]'::jsonb, '[{"language": "main", "value": "Storage"}, {"language": "en", "value": "Storage"}, {"language": "ru", "value": "Хранение"}, {"language": "kk", "value": "Сақтау"}]'::jsonb, '[{"language": "main", "value": "Storage"}, {"language": "en", "value": "Storage"}, {"language": "ru", "value": "Хранение"}, {"language": "kk", "value": "Сақтау"}]'::jsonb, '[{"language": "main", "value": "Storage in Home goods"}, {"language": "en", "value": "Storage in Home goods"}, {"language": "ru", "value": "Хранение в разделе Товары для дома"}, {"language": "kk", "value": "Сақтау — Үй тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('423bfaf6-e121-5e8c-a1c6-3c1a470c26cd'::uuid, '["eb9d2fee-eafa-59aa-8abd-a359a9e8bcc4"]'::jsonb, '[{"language": "main", "value": "Personal hygiene"}, {"language": "en", "value": "Personal hygiene"}, {"language": "ru", "value": "Личная гигиена"}, {"language": "kk", "value": "Жеке гигиена"}]'::jsonb, '[{"language": "main", "value": "Personal hygiene"}, {"language": "en", "value": "Personal hygiene"}, {"language": "ru", "value": "Личная гигиена"}, {"language": "kk", "value": "Жеке гигиена"}]'::jsonb, '[{"language": "main", "value": "Personal hygiene in Beauty & health"}, {"language": "en", "value": "Personal hygiene in Beauty & health"}, {"language": "ru", "value": "Личная гигиена в разделе Красота и здоровье"}, {"language": "kk", "value": "Жеке гигиена — Сұлулық және денсаулық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('0e0bbb5f-ceb2-593a-9fea-72314b1d61e1'::uuid, '["eb9d2fee-eafa-59aa-8abd-a359a9e8bcc4"]'::jsonb, '[{"language": "main", "value": "First aid"}, {"language": "en", "value": "First aid"}, {"language": "ru", "value": "Первая помощь"}, {"language": "kk", "value": "Алғашқы көмек"}]'::jsonb, '[{"language": "main", "value": "First aid"}, {"language": "en", "value": "First aid"}, {"language": "ru", "value": "Первая помощь"}, {"language": "kk", "value": "Алғашқы көмек"}]'::jsonb, '[{"language": "main", "value": "First aid in Beauty & health"}, {"language": "en", "value": "First aid in Beauty & health"}, {"language": "ru", "value": "Первая помощь в разделе Красота и здоровье"}, {"language": "kk", "value": "Алғашқы көмек — Сұлулық және денсаулық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('677d7be4-8302-5ca4-924d-3e24b6379516'::uuid, '["eb9d2fee-eafa-59aa-8abd-a359a9e8bcc4"]'::jsonb, '[{"language": "main", "value": "Hair care"}, {"language": "en", "value": "Hair care"}, {"language": "ru", "value": "Уход за волосами"}, {"language": "kk", "value": "Шаш күтімі"}]'::jsonb, '[{"language": "main", "value": "Hair care"}, {"language": "en", "value": "Hair care"}, {"language": "ru", "value": "Уход за волосами"}, {"language": "kk", "value": "Шаш күтімі"}]'::jsonb, '[{"language": "main", "value": "Hair care in Beauty & health"}, {"language": "en", "value": "Hair care in Beauty & health"}, {"language": "ru", "value": "Уход за волосами в разделе Красота и здоровье"}, {"language": "kk", "value": "Шаш күтімі — Сұлулық және денсаулық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('779e0692-097d-58fa-ba47-69868a348e09'::uuid, '["eb9d2fee-eafa-59aa-8abd-a359a9e8bcc4"]'::jsonb, '[{"language": "main", "value": "Skin care"}, {"language": "en", "value": "Skin care"}, {"language": "ru", "value": "Уход за кожей"}, {"language": "kk", "value": "Тері күтімі"}]'::jsonb, '[{"language": "main", "value": "Skin care"}, {"language": "en", "value": "Skin care"}, {"language": "ru", "value": "Уход за кожей"}, {"language": "kk", "value": "Тері күтімі"}]'::jsonb, '[{"language": "main", "value": "Skin care in Beauty & health"}, {"language": "en", "value": "Skin care in Beauty & health"}, {"language": "ru", "value": "Уход за кожей в разделе Красота и здоровье"}, {"language": "kk", "value": "Тері күтімі — Сұлулық және денсаулық бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('65d133ef-39b5-52d2-bc47-bb627823f820'::uuid, '["7403d215-4893-5173-bbc0-487f436f6081"]'::jsonb, '[{"language": "main", "value": "Pens & pencils"}, {"language": "en", "value": "Pens & pencils"}, {"language": "ru", "value": "Ручки и карандаши"}, {"language": "kk", "value": "Қаламдар мен қарындаштар"}]'::jsonb, '[{"language": "main", "value": "Pens & pencils"}, {"language": "en", "value": "Pens & pencils"}, {"language": "ru", "value": "Ручки и карандаши"}, {"language": "kk", "value": "Қаламдар мен қарындаштар"}]'::jsonb, '[{"language": "main", "value": "Pens & pencils in Stationery"}, {"language": "en", "value": "Pens & pencils in Stationery"}, {"language": "ru", "value": "Ручки и карандаши в разделе Канцтовары"}, {"language": "kk", "value": "Қаламдар мен қарындаштар — Кеңсе тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('6ff6d008-14a8-5468-8a2d-3a8cabad50ce'::uuid, '["7403d215-4893-5173-bbc0-487f436f6081"]'::jsonb, '[{"language": "main", "value": "Notebooks"}, {"language": "en", "value": "Notebooks"}, {"language": "ru", "value": "Тетради и блокноты"}, {"language": "kk", "value": "Дәптерлер"}]'::jsonb, '[{"language": "main", "value": "Notebooks"}, {"language": "en", "value": "Notebooks"}, {"language": "ru", "value": "Тетради и блокноты"}, {"language": "kk", "value": "Дәптерлер"}]'::jsonb, '[{"language": "main", "value": "Notebooks in Stationery"}, {"language": "en", "value": "Notebooks in Stationery"}, {"language": "ru", "value": "Тетради и блокноты в разделе Канцтовары"}, {"language": "kk", "value": "Дәптерлер — Кеңсе тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('2a1ea5f4-a6a1-5526-981d-f8b32e346fd5'::uuid, '["7403d215-4893-5173-bbc0-487f436f6081"]'::jsonb, '[{"language": "main", "value": "Office supplies"}, {"language": "en", "value": "Office supplies"}, {"language": "ru", "value": "Офисные товары"}, {"language": "kk", "value": "Кеңсе керек-жарақтары"}]'::jsonb, '[{"language": "main", "value": "Office supplies"}, {"language": "en", "value": "Office supplies"}, {"language": "ru", "value": "Офисные товары"}, {"language": "kk", "value": "Кеңсе керек-жарақтары"}]'::jsonb, '[{"language": "main", "value": "Office supplies in Stationery"}, {"language": "en", "value": "Office supplies in Stationery"}, {"language": "ru", "value": "Офисные товары в разделе Канцтовары"}, {"language": "kk", "value": "Кеңсе керек-жарақтары — Кеңсе тауарлары бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b0ad845d-5fe2-5ab5-973c-b41b1aaca41c'::uuid, '["53cf4256-e165-5151-b17d-73e03cdc7e92"]'::jsonb, '[{"language": "main", "value": "Books"}, {"language": "en", "value": "Books"}, {"language": "ru", "value": "Книги"}, {"language": "kk", "value": "Кітаптар"}]'::jsonb, '[{"language": "main", "value": "Books"}, {"language": "en", "value": "Books"}, {"language": "ru", "value": "Книги"}, {"language": "kk", "value": "Кітаптар"}]'::jsonb, '[{"language": "main", "value": "Books in Books & media"}, {"language": "en", "value": "Books in Books & media"}, {"language": "ru", "value": "Книги в разделе Книги и медиа"}, {"language": "kk", "value": "Кітаптар — Кітаптар және медиа бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('3b9426f4-cf62-5001-8bd1-55ec92959190'::uuid, '["53cf4256-e165-5151-b17d-73e03cdc7e92"]'::jsonb, '[{"language": "main", "value": "Magazines"}, {"language": "en", "value": "Magazines"}, {"language": "ru", "value": "Журналы"}, {"language": "kk", "value": "Журналдар"}]'::jsonb, '[{"language": "main", "value": "Magazines"}, {"language": "en", "value": "Magazines"}, {"language": "ru", "value": "Журналы"}, {"language": "kk", "value": "Журналдар"}]'::jsonb, '[{"language": "main", "value": "Magazines in Books & media"}, {"language": "en", "value": "Magazines in Books & media"}, {"language": "ru", "value": "Журналы в разделе Книги и медиа"}, {"language": "kk", "value": "Журналдар — Кітаптар және медиа бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('c5414fff-4bd2-550a-ad07-f9473c93ce63'::uuid, '["53cf4256-e165-5151-b17d-73e03cdc7e92"]'::jsonb, '[{"language": "main", "value": "Coloring books"}, {"language": "en", "value": "Coloring books"}, {"language": "ru", "value": "Раскраски"}, {"language": "kk", "value": "Бояу кітаптары"}]'::jsonb, '[{"language": "main", "value": "Coloring books"}, {"language": "en", "value": "Coloring books"}, {"language": "ru", "value": "Раскраски"}, {"language": "kk", "value": "Бояу кітаптары"}]'::jsonb, '[{"language": "main", "value": "Coloring books in Books & media"}, {"language": "en", "value": "Coloring books in Books & media"}, {"language": "ru", "value": "Раскраски в разделе Книги и медиа"}, {"language": "kk", "value": "Бояу кітаптары — Кітаптар және медиа бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('15ee9222-e124-595e-a6b5-8264f377135e'::uuid, '["e63d94b5-b1a1-5b4f-b281-27ebc3b02431"]'::jsonb, '[{"language": "main", "value": "Sports equipment"}, {"language": "en", "value": "Sports equipment"}, {"language": "ru", "value": "Спорттовары"}, {"language": "kk", "value": "Спорт тауарлары"}]'::jsonb, '[{"language": "main", "value": "Sports equipment"}, {"language": "en", "value": "Sports equipment"}, {"language": "ru", "value": "Спорттовары"}, {"language": "kk", "value": "Спорт тауарлары"}]'::jsonb, '[{"language": "main", "value": "Sports equipment in Sports & outdoors"}, {"language": "en", "value": "Sports equipment in Sports & outdoors"}, {"language": "ru", "value": "Спорттовары в разделе Спорт и отдых"}, {"language": "kk", "value": "Спорт тауарлары — Спорт және демалыс бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('82c414d5-2666-56a0-ae3b-8ffc467ba138'::uuid, '["e63d94b5-b1a1-5b4f-b281-27ebc3b02431"]'::jsonb, '[{"language": "main", "value": "Camping"}, {"language": "en", "value": "Camping"}, {"language": "ru", "value": "Кемпинг"}, {"language": "kk", "value": "Кемпинг"}]'::jsonb, '[{"language": "main", "value": "Camping"}, {"language": "en", "value": "Camping"}, {"language": "ru", "value": "Кемпинг"}, {"language": "kk", "value": "Кемпинг"}]'::jsonb, '[{"language": "main", "value": "Camping in Sports & outdoors"}, {"language": "en", "value": "Camping in Sports & outdoors"}, {"language": "ru", "value": "Кемпинг в разделе Спорт и отдых"}, {"language": "kk", "value": "Кемпинг — Спорт және демалыс бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('67faa5b4-0166-5d8f-b9d8-67f9f555ebcb'::uuid, '["e63d94b5-b1a1-5b4f-b281-27ebc3b02431"]'::jsonb, '[{"language": "main", "value": "Balls"}, {"language": "en", "value": "Balls"}, {"language": "ru", "value": "Мячи"}, {"language": "kk", "value": "Доптар"}]'::jsonb, '[{"language": "main", "value": "Balls"}, {"language": "en", "value": "Balls"}, {"language": "ru", "value": "Мячи"}, {"language": "kk", "value": "Доптар"}]'::jsonb, '[{"language": "main", "value": "Balls in Sports & outdoors"}, {"language": "en", "value": "Balls in Sports & outdoors"}, {"language": "ru", "value": "Мячи в разделе Спорт и отдых"}, {"language": "kk", "value": "Доптар — Спорт және демалыс бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('95e16f00-2166-5aec-ac06-470e6c05190c'::uuid, '["e63d94b5-b1a1-5b4f-b281-27ebc3b02431"]'::jsonb, '[{"language": "main", "value": "Swimming goods"}, {"language": "en", "value": "Swimming goods"}, {"language": "ru", "value": "Товары для плавания"}, {"language": "kk", "value": "Жүзу тауарлары"}]'::jsonb, '[{"language": "main", "value": "Swimming goods"}, {"language": "en", "value": "Swimming goods"}, {"language": "ru", "value": "Товары для плавания"}, {"language": "kk", "value": "Жүзу тауарлары"}]'::jsonb, '[{"language": "main", "value": "Swimming goods in Sports & outdoors"}, {"language": "en", "value": "Swimming goods in Sports & outdoors"}, {"language": "ru", "value": "Товары для плавания в разделе Спорт и отдых"}, {"language": "kk", "value": "Жүзу тауарлары — Спорт және демалыс бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('2db7244c-f8f8-5f10-b543-ce22233ada9a'::uuid, '["6f81dc41-eade-5c1c-9ac4-92f2bdcee60a"]'::jsonb, '[{"language": "main", "value": "Pet food"}, {"language": "en", "value": "Pet food"}, {"language": "ru", "value": "Корм для животных"}, {"language": "kk", "value": "Жануарлар азығы"}]'::jsonb, '[{"language": "main", "value": "Pet food"}, {"language": "en", "value": "Pet food"}, {"language": "ru", "value": "Корм для животных"}, {"language": "kk", "value": "Жануарлар азығы"}]'::jsonb, '[{"language": "main", "value": "Pet food in Pet products"}, {"language": "en", "value": "Pet food in Pet products"}, {"language": "ru", "value": "Корм для животных в разделе Товары для животных"}, {"language": "kk", "value": "Жануарлар азығы — Жануарларға арналған тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('54d2c124-d060-5678-bf1d-1d15b0b15908'::uuid, '["6f81dc41-eade-5c1c-9ac4-92f2bdcee60a"]'::jsonb, '[{"language": "main", "value": "Pet accessories"}, {"language": "en", "value": "Pet accessories"}, {"language": "ru", "value": "Аксессуары для животных"}, {"language": "kk", "value": "Жануарлар аксессуарлары"}]'::jsonb, '[{"language": "main", "value": "Pet accessories"}, {"language": "en", "value": "Pet accessories"}, {"language": "ru", "value": "Аксессуары для животных"}, {"language": "kk", "value": "Жануарлар аксессуарлары"}]'::jsonb, '[{"language": "main", "value": "Pet accessories in Pet products"}, {"language": "en", "value": "Pet accessories in Pet products"}, {"language": "ru", "value": "Аксессуары для животных в разделе Товары для животных"}, {"language": "kk", "value": "Жануарлар аксессуарлары — Жануарларға арналған тауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('1a981e8e-3d66-5e57-9c7c-b11f76880d72'::uuid, '["2c5f8f60-69e5-514f-b916-3c54320d8941"]'::jsonb, '[{"language": "main", "value": "Car accessories"}, {"language": "en", "value": "Car accessories"}, {"language": "ru", "value": "Автоаксессуары"}, {"language": "kk", "value": "Авто аксессуарлар"}]'::jsonb, '[{"language": "main", "value": "Car accessories"}, {"language": "en", "value": "Car accessories"}, {"language": "ru", "value": "Автоаксессуары"}, {"language": "kk", "value": "Авто аксессуарлар"}]'::jsonb, '[{"language": "main", "value": "Car accessories in Automotive"}, {"language": "en", "value": "Car accessories in Automotive"}, {"language": "ru", "value": "Автоаксессуары в разделе Автотовары"}, {"language": "kk", "value": "Авто аксессуарлар — Автотауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b6167adc-a013-5e55-ab4b-641022d3fd11'::uuid, '["2c5f8f60-69e5-514f-b916-3c54320d8941"]'::jsonb, '[{"language": "main", "value": "Car care"}, {"language": "en", "value": "Car care"}, {"language": "ru", "value": "Автоуход"}, {"language": "kk", "value": "Авто күтім"}]'::jsonb, '[{"language": "main", "value": "Car care"}, {"language": "en", "value": "Car care"}, {"language": "ru", "value": "Автоуход"}, {"language": "kk", "value": "Авто күтім"}]'::jsonb, '[{"language": "main", "value": "Car care in Automotive"}, {"language": "en", "value": "Car care in Automotive"}, {"language": "ru", "value": "Автоуход в разделе Автотовары"}, {"language": "kk", "value": "Авто күтім — Автотауарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('358424f0-1db6-595c-9690-deed8871e1d1'::uuid, '["2ec60922-66c7-574a-818d-f523f1d7540f"]'::jsonb, '[{"language": "main", "value": "Jewelry"}, {"language": "en", "value": "Jewelry"}, {"language": "ru", "value": "Украшения"}, {"language": "kk", "value": "Әшекейлер"}]'::jsonb, '[{"language": "main", "value": "Jewelry"}, {"language": "en", "value": "Jewelry"}, {"language": "ru", "value": "Украшения"}, {"language": "kk", "value": "Әшекейлер"}]'::jsonb, '[{"language": "main", "value": "Jewelry in Accessories"}, {"language": "en", "value": "Jewelry in Accessories"}, {"language": "ru", "value": "Украшения в разделе Аксессуары"}, {"language": "kk", "value": "Әшекейлер — Аксессуарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('b88f3aa6-c655-5bb8-84bc-ba51e3d0f512'::uuid, '["2ec60922-66c7-574a-818d-f523f1d7540f"]'::jsonb, '[{"language": "main", "value": "Bags & wallets"}, {"language": "en", "value": "Bags & wallets"}, {"language": "ru", "value": "Сумки и кошельки"}, {"language": "kk", "value": "Сөмкелер мен әмияндар"}]'::jsonb, '[{"language": "main", "value": "Bags & wallets"}, {"language": "en", "value": "Bags & wallets"}, {"language": "ru", "value": "Сумки и кошельки"}, {"language": "kk", "value": "Сөмкелер мен әмияндар"}]'::jsonb, '[{"language": "main", "value": "Bags & wallets in Accessories"}, {"language": "en", "value": "Bags & wallets in Accessories"}, {"language": "ru", "value": "Сумки и кошельки в разделе Аксессуары"}, {"language": "kk", "value": "Сөмкелер мен әмияндар — Аксессуарлар бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('cfd36d3d-783e-5c7d-a448-e1839f15ea4b'::uuid, '["b6c5ec8b-c718-5afa-9722-6516274daca9"]'::jsonb, '[{"language": "main", "value": "Delivery"}, {"language": "en", "value": "Delivery"}, {"language": "ru", "value": "Доставка"}, {"language": "kk", "value": "Жеткізу"}]'::jsonb, '[{"language": "main", "value": "Delivery"}, {"language": "en", "value": "Delivery"}, {"language": "ru", "value": "Доставка"}, {"language": "kk", "value": "Жеткізу"}]'::jsonb, '[{"language": "main", "value": "Delivery in Services"}, {"language": "en", "value": "Delivery in Services"}, {"language": "ru", "value": "Доставка в разделе Услуги"}, {"language": "kk", "value": "Жеткізу — Қызметтер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('796206aa-94e2-5d20-95e2-31aa4a9563b5'::uuid, '["b6c5ec8b-c718-5afa-9722-6516274daca9"]'::jsonb, '[{"language": "main", "value": "Repair"}, {"language": "en", "value": "Repair"}, {"language": "ru", "value": "Ремонт"}, {"language": "kk", "value": "Жөндеу"}]'::jsonb, '[{"language": "main", "value": "Repair"}, {"language": "en", "value": "Repair"}, {"language": "ru", "value": "Ремонт"}, {"language": "kk", "value": "Жөндеу"}]'::jsonb, '[{"language": "main", "value": "Repair in Services"}, {"language": "en", "value": "Repair in Services"}, {"language": "ru", "value": "Ремонт в разделе Услуги"}, {"language": "kk", "value": "Жөндеу — Қызметтер бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('d8723cbe-0370-51fb-b08f-0cfb23b3966e'::uuid, '["177d0dae-a19b-57b7-ac66-0d65ad9a6056"]'::jsonb, '[{"language": "main", "value": "Cleaning products"}, {"language": "en", "value": "Cleaning products"}, {"language": "ru", "value": "Средства для уборки"}, {"language": "kk", "value": "Тазалау құралдары"}]'::jsonb, '[{"language": "main", "value": "Cleaning products"}, {"language": "en", "value": "Cleaning products"}, {"language": "ru", "value": "Средства для уборки"}, {"language": "kk", "value": "Тазалау құралдары"}]'::jsonb, '[{"language": "main", "value": "Cleaning products in Household chemicals"}, {"language": "en", "value": "Cleaning products in Household chemicals"}, {"language": "ru", "value": "Средства для уборки в разделе Бытовая химия"}, {"language": "kk", "value": "Тазалау құралдары — Тұрмыстық химия бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('8f687d24-8f99-51f9-a061-a41fc8aeffbe'::uuid, '["177d0dae-a19b-57b7-ac66-0d65ad9a6056"]'::jsonb, '[{"language": "main", "value": "Laundry products"}, {"language": "en", "value": "Laundry products"}, {"language": "ru", "value": "Средства для стирки"}, {"language": "kk", "value": "Кір жуу құралдары"}]'::jsonb, '[{"language": "main", "value": "Laundry products"}, {"language": "en", "value": "Laundry products"}, {"language": "ru", "value": "Средства для стирки"}, {"language": "kk", "value": "Кір жуу құралдары"}]'::jsonb, '[{"language": "main", "value": "Laundry products in Household chemicals"}, {"language": "en", "value": "Laundry products in Household chemicals"}, {"language": "ru", "value": "Средства для стирки в разделе Бытовая химия"}, {"language": "kk", "value": "Кір жуу құралдары — Тұрмыстық химия бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;
INSERT INTO generic_goods_categories (id, type_ids, name, alias, description, quantity_unit_id, image_paths)
VALUES ('97930d8d-2b9c-50ad-a7a2-837330758faa'::uuid, '["177d0dae-a19b-57b7-ac66-0d65ad9a6056"]'::jsonb, '[{"language": "main", "value": "Dishwashing"}, {"language": "en", "value": "Dishwashing"}, {"language": "ru", "value": "Средства для посуды"}, {"language": "kk", "value": "Ыдыс жуу құралдары"}]'::jsonb, '[{"language": "main", "value": "Dishwashing"}, {"language": "en", "value": "Dishwashing"}, {"language": "ru", "value": "Средства для посуды"}, {"language": "kk", "value": "Ыдыс жуу құралдары"}]'::jsonb, '[{"language": "main", "value": "Dishwashing in Household chemicals"}, {"language": "en", "value": "Dishwashing in Household chemicals"}, {"language": "ru", "value": "Средства для посуды в разделе Бытовая химия"}, {"language": "kk", "value": "Ыдыс жуу құралдары — Тұрмыстық химия бөлімінде"}]'::jsonb, '0', '[]'::jsonb)
ON CONFLICT (id) DO UPDATE SET
  type_ids = EXCLUDED.type_ids,
  name = EXCLUDED.name,
  alias = EXCLUDED.alias,
  description = EXCLUDED.description,
  quantity_unit_id = EXCLUDED.quantity_unit_id,
  image_paths = EXCLUDED.image_paths;