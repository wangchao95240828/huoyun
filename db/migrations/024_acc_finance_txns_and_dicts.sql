-- ACC 财务流水（customer/supplier × adjust/refund/rebate）+ 2 字典批量迁移
-- 对应 ACC 旧表 + 类：
--   CAdjust.php / SAdjust    → acc_finance_txns side=*, txn_type='ADJUST'   调账
--   CRefund.php / SRefund    → acc_finance_txns side=*, txn_type='REFUND'   退款
--   Rebate.php (c/s)         → acc_finance_txns side=*, txn_type='REBATE'   返利
--   ExpensesClass.php        → acc_expense_categories
--   FeeType.php (sub)        → acc_fee_item_types

-- ─────────────────────────────────────────────────────────────────────────────
-- 1) 财务流水统一表（6 用途共享）
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_finance_txns (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  txn_no text not null,
  the_date date,
  side text not null
    check (side in ('CUSTOMER', 'SUPPLIER')),
  txn_type text not null
    check (txn_type in ('ADJUST', 'REFUND', 'REBATE')),
  customer_id uuid references customers(id),
  partner_id uuid references partners(id),
  amount numeric(14, 2) not null default 0,
  currency char(3) not null default 'CNY',
  reason text,
  remark text,
  status text not null default 'PENDING'
    check (status in ('PENDING', 'APPROVED', 'REJECTED', 'PAID')),
  add_name text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, txn_no, txn_type)
);
create index idx_acc_finance_txns_lookup
  on acc_finance_txns(tenant_id, side, txn_type, the_date desc);
alter table acc_finance_txns enable row level security;
alter table acc_finance_txns force row level security;
create policy acc_finance_txns_tenant_isolation on acc_finance_txns
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 2) 费用分类
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_expense_categories (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  expense_type text,
  is_coming boolean not null default false,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table acc_expense_categories enable row level security;
alter table acc_expense_categories force row level security;
create policy acc_expense_categories_tenant_isolation on acc_expense_categories
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- ─────────────────────────────────────────────────────────────────────────────
-- 3) 费用项类型
-- ─────────────────────────────────────────────────────────────────────────────
create table acc_fee_item_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  code text not null,
  name text not null,
  fee_type text,
  color text,
  remark text,
  audit_status text not null default 'PENDING'
    check (audit_status in ('PENDING', 'AUDITED', 'UNAUDITED')),
  audited_at timestamptz,
  audit_name text,
  created_at timestamptz not null default now(),
  unique (tenant_id, code)
);
alter table acc_fee_item_types enable row level security;
alter table acc_fee_item_types force row level security;
create policy acc_fee_item_types_tenant_isolation on acc_fee_item_types
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));
