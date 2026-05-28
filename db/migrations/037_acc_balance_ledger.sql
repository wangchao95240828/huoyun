-- ACC Customer_Balance_History 等价：资金账户流水（balance_ledger）。
--
-- 对照 docs/acc-logic-gap-claude-task-2026-05-28.md §3.4 + §4 任务3。
--
-- 与现有 ledger_*（复式记账总账，借贷平衡、面向会计科目）区分：
--   - ledger_accounts/ledger_entries/ledger_transactions = 会计总账（未来对接财务系统）
--   - balance_ledger = 面向客户/供应商资金账户的"余额流水"，单边记录，
--     带 before/after balance + 业务对象 + 操作人，对应旧 ACC Customer_Balance_History。
--
-- 每次 financial_accounts.balance 变动都应配一条 balance_ledger，使余额可追溯来源。

create type balance_ledger_direction as enum ('CREDIT', 'DEBIT');
-- CREDIT = 余额增加（充值/收款/退回/释放）；DEBIT = 余额减少（预扣/付款）

create type balance_ledger_biz_type as enum (
  'PREPAY',          -- 下单预扣
  'PREPAY_RELEASE',  -- 取消订单释放预扣
  'RECEIPT',         -- 客户收款（充值）
  'PAYMENT',         -- 供应商付款
  'REFUND',          -- 退款
  'ADJUST',          -- 调账
  'REBATE',          -- 返利
  'FINE',            -- 罚款
  'COMPENSATE',      -- 赔偿
  'VOID',            -- 作废冲正
  'FX_DIFF'          -- 汇率差
);

create table balance_ledger (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  account_id uuid not null references financial_accounts(id),
  owner_type text not null,           -- CUSTOMER / SUPPLIER
  owner_id uuid,                       -- 客户/供应商 id
  biz_type balance_ledger_biz_type not null,
  source_type text,                    -- 业务对象类型：order / shipment / invoice / charge / payment
  source_id uuid,                      -- 业务对象 id
  source_ref text,                     -- 业务单号（order_no / invoice_no 等）
  currency char(3) not null,
  direction balance_ledger_direction not null,
  amount numeric(18, 2) not null check (amount >= 0),     -- 始终正数，方向由 direction 表达
  balance_before numeric(18, 2) not null,
  balance_after numeric(18, 2) not null,
  operator text,                       -- 操作人（用户名 / 客户编码 / SYSTEM）
  remark text,
  created_at timestamptz not null default now()
);

alter table balance_ledger enable row level security;
alter table balance_ledger force row level security;
create policy balance_ledger_tenant_isolation on balance_ledger
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 按账户 + 时间倒序查流水（余额追溯主查询）
create index balance_ledger_account_idx
  on balance_ledger(tenant_id, account_id, created_at desc);

-- 按业务对象反查流水
create index balance_ledger_source_idx
  on balance_ledger(tenant_id, source_type, source_id)
  where source_id is not null;

-- 按客户/供应商查流水
create index balance_ledger_owner_idx
  on balance_ledger(tenant_id, owner_type, owner_id, created_at desc)
  where owner_id is not null;
