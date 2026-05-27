-- ACC 客户产品 + 系统杂项：
--   channel_accounts / product_items / sold_tos / potentials / notices
--   logistics_interfaces / scheduled_tasks / message_templates
-- products 复用 rate_cards + services；quick-orders 复用 orders 视图。
-- 对应 ACC 旧类：
--   ChannelAccount.php    → acc_channel_accounts
--   ProductItem.php       → acc_product_items
--   SoldTo.php            → acc_sold_tos
--   Potential.php         → acc_potentials
--   Notice.php            → acc_notices
--   LogisticsInterface.php → acc_logistics_interfaces
--   Task.php              → acc_scheduled_tasks
--   Template.php          → acc_message_templates

-- 1) 渠道账号（FK channels + partners）
create table acc_channel_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  channel_id uuid not null references channels(id),
  partner_id uuid references partners(id),
  account_no text,
  account_name text,
  api_key text,
  api_secret text,
  endpoint_url text,
  is_active boolean default true,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_channel_accounts enable row level security;
alter table acc_channel_accounts force row level security;
create policy acc_channel_accounts_tenant_isolation on acc_channel_accounts
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 2) 品名管理
create table acc_product_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  name_en text,
  hs_code text,
  category text,
  unit_price numeric(14,2),
  currency char(3) default 'CNY',
  is_active boolean default true,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table acc_product_items enable row level security;
alter table acc_product_items force row level security;
create policy acc_product_items_tenant_isolation on acc_product_items
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 3) 收件地址库
create table acc_sold_tos (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid references customers(id),
  contact_name text,
  contact_mobile text,
  company_name text,
  country text,
  state text,
  city text,
  address text,
  postcode text,
  is_default boolean default false,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_sold_tos enable row level security;
alter table acc_sold_tos force row level security;
create policy acc_sold_tos_tenant_isolation on acc_sold_tos
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 4) 潜在客户
create table acc_potentials (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  company_name text,
  contact_name text,
  contact_mobile text,
  source text,
  status text default 'NEW' check (status in ('NEW','CONTACTED','NEGOTIATING','WON','LOST')),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_potentials enable row level security;
alter table acc_potentials force row level security;
create policy acc_potentials_tenant_isolation on acc_potentials
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 5) 客户通知（含已读/未读）
create table acc_notices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  title text not null,
  content text,
  notice_type text default 'INFO' check (notice_type in ('INFO','WARNING','URGENT')),
  target_type text check (target_type in ('ALL','CUSTOMER','CHANNEL','EMPLOYEE')),
  target_id uuid,
  is_read boolean default false,
  read_at timestamptz,
  read_by text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_notices enable row level security;
alter table acc_notices force row level security;
create policy acc_notices_tenant_isolation on acc_notices
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 6) 第三方物流接口配置
create table acc_logistics_interfaces (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  provider_code text,
  api_key text,
  api_secret text,
  endpoint_url text,
  is_enabled boolean default false,
  config_json text,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_logistics_interfaces enable row level security;
alter table acc_logistics_interfaces force row level security;
create policy acc_logistics_interfaces_tenant_isolation on acc_logistics_interfaces
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 7) 定时任务
create table acc_scheduled_tasks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  task_name text not null,
  task_type text,
  cron_expr text,
  is_enabled boolean default true,
  last_run_at timestamptz,
  next_run_at timestamptz,
  config_json text,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_scheduled_tasks enable row level security;
alter table acc_scheduled_tasks force row level security;
create policy acc_scheduled_tasks_tenant_isolation on acc_scheduled_tasks
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 8) 消息模板
create table acc_message_templates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  template_name text not null,
  template_type text check (template_type in ('SMS','EMAIL','WECHAT','SYSTEM')),
  title text,
  content text,
  is_active boolean default true,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_message_templates enable row level security;
alter table acc_message_templates force row level security;
create policy acc_message_templates_tenant_isolation on acc_message_templates
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
