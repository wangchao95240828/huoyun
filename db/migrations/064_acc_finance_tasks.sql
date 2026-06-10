-- 029: 财务模块任务 A1-A5 / B1 / C2 配套字段
-- A1: customers.salesman_id (已在 F6 加过，这里补 IF NOT EXISTS)
-- A4: customer_invoices.line_count + previous_balance + 序列
-- A5: customer_balance_accounts + balance_ledger
-- B1: acc_finance_txns.verify_status + charge_items ADJUST

-- =============================================================================
-- 1) A1: customers.salesman_id（业务员关联）
-- =============================================================================
-- alair 原版引用 employees，但 XQT 表名是 acc_employees（alair 分支当时未 sync）
ALTER TABLE customers
  ADD COLUMN IF NOT EXISTS salesman_id uuid REFERENCES acc_employees(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS customers_salesman_idx ON customers (salesman_id);

COMMENT ON COLUMN customers.salesman_id IS '业务员关联（用于应收款项目业务员列）';

-- =============================================================================
-- 2) A4: customer_invoices 补字段 + 序列
-- =============================================================================
ALTER TABLE customer_invoices
  ADD COLUMN IF NOT EXISTS line_count integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS previous_balance numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_payment_at timestamptz;

COMMENT ON COLUMN customer_invoices.line_count IS '关联 charges 条数（F2 字段）';
COMMENT ON COLUMN customer_invoices.previous_balance IS '上期未付余额（F10 字段）';
COMMENT ON COLUMN customer_invoices.last_payment_at IS '最近一次收款时间（F3 字段）';

-- 序列：用于客户账单编号 CINV-YYYYMM-NNNN
CREATE SEQUENCE IF NOT EXISTS cinv_invoice_seq START 1;

-- =============================================================================
-- 3) A5: 客户余额账户 + 银行余额变动明细
-- =============================================================================
CREATE TABLE IF NOT EXISTS customer_balance_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  customer_id uuid not null references customers(id),
  currency char(3) not null default 'CNY',
  balance numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, customer_id, currency)
);

ALTER TABLE customer_balance_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_balance_accounts FORCE ROW LEVEL SECURITY;
CREATE POLICY customer_balance_accounts_tenant_isolation ON customer_balance_accounts
  USING (app_tenant_matches(tenant_id))
  WITH CHECK (app_tenant_matches(tenant_id));

CREATE TABLE IF NOT EXISTS balance_ledger (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  account_id uuid not null references financial_accounts(id),
  type text not null check (type in ('IN', 'OUT', 'CREDIT', 'DEBIT')),
  amount numeric(18,2) not null,
  balance_before numeric(18,2) not null,
  balance_after numeric(18,2) not null,
  source_type text,
  source_id uuid,
  remark text,
  created_at timestamptz not null default now()
);

ALTER TABLE balance_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE balance_ledger FORCE ROW LEVEL SECURITY;
CREATE POLICY balance_ledger_tenant_isolation ON balance_ledger
  USING (app_tenant_matches(tenant_id))
  WITH CHECK (app_tenant_matches(tenant_id));

-- 银行账户余额明细索引
CREATE INDEX IF NOT EXISTS idx_balance_ledger_account ON balance_ledger(tenant_id, account_id, created_at desc);
CREATE INDEX IF NOT EXISTS idx_balance_ledger_source ON balance_ledger(tenant_id, source_type, source_id);

-- receivable_settlements 补 bank_account_id（如果还没加）
ALTER TABLE receivable_settlements
  ADD COLUMN IF NOT EXISTS bank_account_id uuid references financial_accounts(id);

-- =============================================================================
-- 4) B1: acc_finance_txns 补 verify_status + charge_items 补 ADJUST
-- =============================================================================
ALTER TABLE acc_finance_txns
  ADD COLUMN IF NOT EXISTS verify_status text NOT NULL DEFAULT 'PENDING'
    check (verify_status in ('PENDING', 'VERIFIED', 'REJECTED')),
  ADD COLUMN IF NOT EXISTS verified_at timestamptz,
  ADD COLUMN IF NOT EXISTS verify_name text;

COMMENT ON COLUMN acc_finance_txns.verify_status IS '二审状态（金额>1000 须先二审通过）';

-- ADJUST 类型费用项（如果还没建）
INSERT INTO charge_items (tenant_id, code, name, category, default_side, default_uom)
SELECT id, 'ADJUST', '调账', 'SURCHARGE', 'AR', 'SHIPMENT'
FROM tenants
WHERE NOT EXISTS (SELECT 1 FROM charge_items WHERE code = 'ADJUST')
LIMIT 1;

-- =============================================================================
-- 5) C2: acc_transfers 补 verify_status（转账二审门槛）
-- =============================================================================
ALTER TABLE acc_transfers
  ADD COLUMN IF NOT EXISTS verify_status text NOT NULL DEFAULT 'PENDING'
    check (verify_status in ('PENDING', 'VERIFIED', 'REJECTED')),
  ADD COLUMN IF NOT EXISTS verified_at timestamptz,
  ADD COLUMN IF NOT EXISTS verify_name text;

COMMENT ON COLUMN acc_transfers.verify_status IS '二审状态（金额>10w 须先二审通过）';
