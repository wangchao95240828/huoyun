-- ACC 物流扩展 + 价格分区：
--   stowage_steps / transits / dispatches / forecasts / track_items / zones（视图）
-- stowages / stowage_categories / stowage_ports 复用 020 已建表，补审计列。
-- 对应 ACC 旧类：
--   Stowage.php        → stowages (020 复用)
--   StowageCategory.php → stowage_categories (020 复用)
--   Port.php           → stowage_ports (020 复用)
--   Dispatches.php     → acc_dispatches
--   Transit.php        → acc_transits
--   Forecast.php       → acc_forecasts
--   Track_item.php     → acc_track_items

-- 0) 复用 020 表补充审计列
alter table stowages
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table stowage_categories
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

alter table stowage_ports
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text;

-- 1) 配载步骤
create table acc_stowage_steps (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  stowage_id uuid references stowages(id),
  step_order int not null default 0,
  name text not null,
  location text,
  status text default 'PENDING' check (status in ('PENDING','IN_PROGRESS','DONE')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_stowage_steps enable row level security;
alter table acc_stowage_steps force row level security;
create policy acc_stowage_steps_tenant_isolation on acc_stowage_steps
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 2) 转运管理（含金额 + 汇率快照）
create table acc_transits (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  transit_no text,
  from_port_id uuid references stowage_ports(id),
  to_port_id uuid references stowage_ports(id),
  shipping_line text,
  vessel text,
  voyage text,
  etd date,
  eta date,
  tariff numeric(14,2),
  cost numeric(14,2),
  currency char(3) default 'CNY',
  status text default 'PENDING' check (status in ('PENDING','IN_TRANSIT','ARRIVED','CUSTOMS','DONE')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_transits enable row level security;
alter table acc_transits force row level security;
create policy acc_transits_tenant_isolation on acc_transits
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 3) 上门揽收
create table acc_dispatches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  dispatch_no text,
  customer_id uuid references customers(id),
  contact_name text,
  contact_mobile text,
  pick_address text,
  pick_date date,
  pick_time_range text,
  package_count int default 0,
  weight numeric(10,2),
  status text default 'PENDING' check (status in ('PENDING','ASSIGNED','PICKED','DONE','CANCELLED')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_dispatches enable row level security;
alter table acc_dispatches force row level security;
create policy acc_dispatches_tenant_isolation on acc_dispatches
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 4) 预报包裹
create table acc_forecasts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  forecast_no text,
  customer_id uuid references customers(id),
  channel_id uuid references channels(id),
  package_count int default 0,
  weight numeric(10,2),
  volume numeric(10,2),
  origin text,
  destination text,
  forecast_date date,
  status text default 'PENDING' check (status in ('PENDING','RECEIVED','IN_WAREHOUSE','SHIPPED')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_forecasts enable row level security;
alter table acc_forecasts force row level security;
create policy acc_forecasts_tenant_isolation on acc_forecasts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 5) 轨迹项目（字典表，tracking_events.raw_status 引用）
create table acc_track_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  name_en text,
  sort_order int default 0,
  is_active boolean default true,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table acc_track_items enable row level security;
alter table acc_track_items force row level security;
create policy acc_track_items_tenant_isolation on acc_track_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 6) zones 是 rate_card_lines.zone_code 聚合视图，不新建表；由 controller 内联聚合。
