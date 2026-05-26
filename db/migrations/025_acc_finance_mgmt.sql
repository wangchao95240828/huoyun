-- ACC 资金管理：expenses / transfers / dividends / borrowings / cycles / assets / received-sms
-- banks 复用 financial_accounts 表（已存在），补审计列。
-- 对应 ACC 旧类：
--   Expenses.php   → acc_expenses
--   Bank.php       → financial_accounts (复用，按 account_type='BANK' 过滤)
--   Transfer       → acc_transfers
--   Dividend.php   → acc_dividends
--   Borrowing.php  → acc_borrowings
--   Assets.php     → acc_assets
--   Cycle.php      → acc_cycles
--   ReceivedSms    → acc_received_sms

-- 1) 费用收支
create table acc_expenses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  the_date date,
  category_id uuid references acc_expense_categories(id),
  category_name text,
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  bank_account_id uuid references financial_accounts(id),
  remark text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_expenses enable row level security;
alter table acc_expenses force row level security;
create policy acc_expenses_tenant_isolation on acc_expenses
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 2) 银行账户：复用 financial_accounts，补审计列
alter table financial_accounts
  add column if not exists audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  add column if not exists audited_at timestamptz,
  add column if not exists audit_name text,
  add column if not exists last_update timestamptz,
  add column if not exists is_show boolean not null default true;

-- 3) 转账记录
create table acc_transfers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  transfer_no text,
  the_date date,
  from_bank_id uuid references financial_accounts(id),
  to_bank_id uuid references financial_accounts(id),
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  remark text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_transfers enable row level security;
alter table acc_transfers force row level security;
create policy acc_transfers_tenant_isolation on acc_transfers
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 4) 分红
create table acc_dividends (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  dividend_no text not null,
  the_date date,
  dividend_type text,
  person_name text,
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  bank_account_id uuid references financial_accounts(id),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, dividend_no)
);
alter table acc_dividends enable row level security;
alter table acc_dividends force row level security;
create policy acc_dividends_tenant_isolation on acc_dividends
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 5) 资金借贷
create table acc_borrowings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  borrower_name text not null,
  the_date date,
  borrowing_type text,
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  rate numeric(8, 4),
  remark text,
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_borrowings enable row level security;
alter table acc_borrowings force row level security;
create policy acc_borrowings_tenant_isolation on acc_borrowings
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 6) 固定资产
create table acc_assets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  the_date date,
  currency char(3) not null default 'CNY',
  amount numeric(14, 2) not null default 0,
  depreciation numeric(14, 2),
  surplus_value numeric(14, 2),
  depreciation_months int,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_assets enable row level security;
alter table acc_assets force row level security;
create policy acc_assets_tenant_isolation on acc_assets
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 7) 周期费用
create table acc_cycles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  name text not null,
  cycle text,                   -- 月/季度/年
  start_date date,
  end_date date,
  currency char(3) not null default 'CNY',
  amount numeric(14, 2) not null default 0,
  bank_account_id uuid references financial_accounts(id),
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_cycles enable row level security;
alter table acc_cycles force row level security;
create policy acc_cycles_tenant_isolation on acc_cycles
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 8) 收款短信
create table acc_received_sms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  source_name text,             -- "工商银行短信"等
  account_no text,
  payer text,
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  sms_time timestamptz,
  bank_account_id uuid references financial_accounts(id),
  raw_content text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now()
);
alter table acc_received_sms enable row level security;
alter table acc_received_sms force row level security;
create policy acc_received_sms_tenant_isolation on acc_received_sms
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
