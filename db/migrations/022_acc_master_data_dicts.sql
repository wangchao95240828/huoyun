-- ACC 主数据字典批量迁移
-- 对应 ACC 旧表：
--   Country         → countries
--   Postcode        → postcodes
--   HsCode          → hs_codes
--   BankName        → bank_names
--   District        → districts
--   Customer_Group  → customer_groups

create table countries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,            -- ISO Alpha-2 (US/GB/CN)
  code3 text,                    -- ISO Alpha-3
  cn_name text not null,
  en_name text not null,
  is_open boolean not null default true,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table countries enable row level security;
alter table countries force row level security;
create policy countries_tenant_isolation on countries
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table postcodes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  country_code text not null,
  postcode text not null,
  region text,
  city text,
  state_code text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, country_code, postcode)
);
create index idx_postcodes_lookup on postcodes(tenant_id, country_code, postcode);
alter table postcodes enable row level security;
alter table postcodes force row level security;
create policy postcodes_tenant_isolation on postcodes
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table hs_codes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name_en text,
  name_cn text not null,
  category text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table hs_codes enable row level security;
alter table hs_codes force row level security;
create policy hs_codes_tenant_isolation on hs_codes
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table bank_names (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  code text,
  swift text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, name)
);
alter table bank_names enable row level security;
alter table bank_names force row level security;
create policy bank_names_tenant_isolation on bank_names
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table districts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  parent_id uuid references districts(id),
  code text not null,
  name text not null,
  level int not null default 1
    check (level between 1 and 4),  -- 1省 2市 3区/县 4街道
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
create index idx_districts_parent on districts(tenant_id, parent_id);
alter table districts enable row level security;
alter table districts force row level security;
create policy districts_tenant_isolation on districts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table customer_groups (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table customer_groups enable row level security;
alter table customer_groups force row level security;
create policy customer_groups_tenant_isolation on customer_groups
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- warehouses 和 return_orders 已存在；补 audit 列
alter table warehouses
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table return_orders
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;
