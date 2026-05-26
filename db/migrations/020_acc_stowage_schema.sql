-- ACC Sync (配载同步) 数据模型迁移
-- 对应 ACC 表：
--   Stowage             → stowages          配载主单
--   Stowage_Category    → stowage_categories 配载分类字典
--   Stowage_Port        → stowage_ports     港口字典
--   Online_Package      ≈ shipments         （不新建）
--   Online_Package_Item ≈ cartons           （新加 stowage_id 列承担 Online_Package_Item.Stowage 关系）

create table stowage_categories (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  code text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, name)
);

alter table stowage_categories enable row level security;
alter table stowage_categories force row level security;
create policy stowage_categories_tenant_isolation on stowage_categories
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table stowage_ports (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  code text,
  created_at timestamptz not null default now(),
  unique (tenant_id, name)
);

alter table stowage_ports enable row level security;
alter table stowage_ports force row level security;
create policy stowage_ports_tenant_isolation on stowage_ports
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

create table stowages (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  stowage_no text not null,
  the_date date,
  stowage_type int not null default 1
    check (stowage_type in (0, 1)),
  category_id uuid references stowage_categories(id),
  count_value int,
  piece_value int,
  quantity_value int,
  weight_value numeric(14, 3),
  volume_value numeric(14, 4),
  declared_value numeric(14, 2),
  tax_amount numeric(14, 2),
  departure_time timestamptz,
  departure_port_id uuid references stowage_ports(id),
  arrival_time timestamptz,
  arrival_port_id uuid references stowage_ports(id),
  remark text,
  add_name text,
  add_time timestamptz,
  modify_time timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, stowage_no)
);
create index idx_stowages_customer on stowages(tenant_id, customer_id);

alter table stowages enable row level security;
alter table stowages force row level security;
create policy stowages_tenant_isolation on stowages
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- cartons 加 stowage_id：对应 ACC Online_Package_Item.Stowage
alter table cartons add column if not exists stowage_id uuid references stowages(id);
create index if not exists idx_cartons_stowage on cartons(tenant_id, stowage_id)
  where stowage_id is not null;
