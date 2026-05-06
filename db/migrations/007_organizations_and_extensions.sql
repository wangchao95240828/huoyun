-- 007: 分公司（组织架构）+ 费用四流扩展 + 账单期间锁定 + 汇率双轨

-- 1. 组织架构表
create table organizations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  parent_id uuid references organizations(id),
  code text not null,
  name text not null,
  org_type text not null default 'branch'
    check (org_type in ('hq', 'branch', 'department')),
  manager_id uuid references users(id),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table organizations enable row level security;
create policy org_tenant_isolation on organizations
  using (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- 2. 核心业务表加 branch_id
alter table shipments add column branch_id uuid references organizations(id);
alter table charges add column branch_id uuid references organizations(id);
alter table customer_invoices add column branch_id uuid references organizations(id);
alter table carrier_bill_imports add column branch_id uuid references organizations(id);
alter table commission_plans add column branch_id uuid references organizations(id);
alter table customers add column branch_id uuid references organizations(id);

create index idx_shipments_branch on shipments(tenant_id, branch_id);
create index idx_charges_branch on charges(tenant_id, branch_id);
create index idx_invoices_branch on customer_invoices(tenant_id, branch_id);

-- 3. 扩展 charge_side 枚举：支持销售成本和销售提成
alter type charge_side add value 'SELLER_COST';
alter type charge_side add value 'SELLER_COMMISSION';

-- 4. 员工/销售关联字段
alter table charges add column seller_id uuid references users(id);
alter table shipments add column seller_id uuid references users(id);
alter table shipments add column servicer_id uuid references users(id);

-- 5. 账单期间锁定
create table billing_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  branch_id uuid references organizations(id),
  period_start timestamptz not null,
  period_end timestamptz not null,
  locked_ar boolean not null default false,
  locked_ap boolean not null default false,
  locked_seller boolean not null default false,
  locked_by uuid references users(id),
  locked_at timestamptz,
  remark text,
  created_at timestamptz not null default now()
);
alter table billing_periods enable row level security;
create policy bp_tenant_isolation on billing_periods
  using (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- 6. 汇率表加 purpose 区分应收/应付
alter table exchange_rates add column if not exists purpose text not null default 'BOTH'
  check (purpose in ('AR', 'AP', 'BOTH'));

-- 7. 数据来源标记（区分 ACC/XQT/LOCAL）
alter table shipments add column source text not null default 'LOCAL'
  check (source in ('ACC', 'XQT', 'LOCAL'));
alter table shipments add column external_id text;
alter table charges add column source text not null default 'LOCAL'
  check (source in ('ACC', 'XQT', 'LOCAL'));
alter table charges add column external_id text;
alter table customers add column source text not null default 'LOCAL'
  check (source in ('ACC', 'XQT', 'LOCAL'));
alter table customers add column external_id text;

create index idx_shipments_source on shipments(tenant_id, source, external_id);
create index idx_charges_source on charges(tenant_id, source, external_id);
create index idx_customers_source on customers(tenant_id, source, external_id);

-- 8. 用户表加 branch_id
alter table users add column branch_id uuid references organizations(id);
