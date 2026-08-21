CREATE EXTENSION IF NOT EXISTS pgcrypto;

create table if not exists users(
  id uuid primary key default gen_random_uuid(),
  phone_number varchar(32) unique not null,
  email varchar(255) unique not null,
  first_name varchar(255) not null,
  last_name varchar(255) not null,
  country_locale varchar(64) not null,
  worker_ids text default null,
  supplier_ids text default null,
  password_hash varchar(100) not null,
  created_at timestamptz not null default now(),
  is_active boolean not null default true
);

create table if not exists stores(
  id uuid primary key default gen_random_uuid(),
  user_ids text not null,
  type_ids text default null,
  name text not null,
  alias text default null,
  description text default null,
  company_forms text default null,
  location text default null,
  phone_numbers text default null,
  emails text default null,
  created_at timestamptz not null default now(),
  is_active boolean not null
);

create table if not exists suppliers(
  id uuid primary key default gen_random_uuid(),
  user_ids text default null,

  type_ids text default null,
  category_ids text default null,

  name text not null,

  phone_numbers text default null,
  emails text default null,

  added_at timestamptz not null default now(),
  is_active boolean not null default true
);

create table if not exists generic_goods_items(
    id uuid primary key default gen_random_uuid(),
    barcode text default null,
    name text not null,
    type_ids text default null,
    category_ids text default null,
    supplier_ids text default null,
    manufacturer_ids text default null
);

create table if not exists manufacturers(
  id uuid primary key default gen_random_uuid(),
  user_ids text default null,

  type_ids text default null,
  category_ids text default null,

  name text not null,

  phone_numbers text default null,
  emails text default null,

  added_at timestamptz not null default now(),
  is_active boolean not null default true
);
