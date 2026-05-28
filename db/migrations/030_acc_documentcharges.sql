-- ACC documentcharges 闭环：在已有 charges / customer_invoices / partner_invoices 之上
-- 补缺失字段并增加 settlement 状态机，让"下单 → 费用 → 账单 → 收付款 → 利润"链路真实可追溯。
--
-- 对照 ACC 老逻辑（acc/inc/Charge.php / CBill.php / Received.php / Pay.php）：
--   - Express_Charge.Paid 已收金额      ↔ charges.paid_amount（已有）→ 补 settle 流转
--   - Express_Charge.Status 状态        ↔ charges.status + settlement_status（新增）
--   - Bill 与 Customer_Invoice 关联     ↔ customer_invoices ↔ customer_invoice_lines（已有）
--   - ACC 用 ProductSave + 利润视图     ↔ AccProfitsController 聚合（不落表）

-- 1) charges 补 paid_amount/unpaid_amount/settlement_status/source_ref/rate_snapshot_id
--    paid_amount/unpaid_amount 既支持 AR（客户已付/未付）也支持 AP（供应商已付/未付）
alter table charges
  add column if not exists paid_amount numeric(14,2) not null default 0,
  add column if not exists unpaid_amount numeric(14,2),
  add column if not exists settlement_status text not null default 'UNSETTLED'
    check (settlement_status in ('UNSETTLED','PARTIAL','SETTLED','VOID')),
  add column if not exists source_ref text,                     -- 业务回溯：order_no / invoice_no
  add column if not exists rate_snapshot_id uuid,               -- 汇率快照引用
  add column if not exists order_id uuid references orders(id), -- 直接挂订单，方便 AccProfits 聚合
  add column if not exists customer_id uuid references customers(id);

-- 已有数据初始化 unpaid_amount = amount - paid_amount
update charges set unpaid_amount = amount - paid_amount
  where unpaid_amount is null;

-- 2) 汇率快照表：金额相关单据都应保存当时的汇率，事后对账可重现
create table if not exists fx_rate_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null references tenants(id),
  from_currency char(3) not null,
  to_currency char(3) not null,
  rate numeric(18,8) not null,
  snapshot_at timestamptz not null default now(),
  source text not null default 'LOCAL',
  metadata jsonb not null default '{}'
);
alter table fx_rate_snapshots enable row level security;
alter table fx_rate_snapshots force row level security;
create policy fx_rate_snapshots_tenant_isolation on fx_rate_snapshots
  using (app_tenant_matches(tenant_id))
  with check (app_tenant_matches(tenant_id));

-- 3) partner_invoice_lines 可能缺 charge_id（用于关联到 AP charges）
alter table partner_invoice_lines
  add column if not exists charge_id uuid references charges(id),
  add column if not exists shipment_id uuid;

-- 4) partner_invoices 状态补齐
alter table partner_invoices
  add column if not exists paid_amount numeric(14,2) not null default 0,
  add column if not exists unpaid_amount numeric(14,2),
  add column if not exists writeoff_status text not null default 'UNPAID'
    check (writeoff_status in ('UNPAID','PARTIAL','PAID','VOID'));

update partner_invoices set unpaid_amount = total_amount - paid_amount
  where unpaid_amount is null;

-- 5) 索引优化：常见 AccProfits 聚合 + 账单生成查询
create index if not exists charges_audit_settle_idx
  on charges(tenant_id, side, audit_status, settlement_status)
  where audit_status = 'AUDITED' and settlement_status <> 'VOID';

create index if not exists charges_customer_idx
  on charges(tenant_id, customer_id, created_at)
  where customer_id is not null;

create index if not exists charges_order_idx
  on charges(tenant_id, order_id)
  where order_id is not null;

-- 6) customer_invoice_lines 关联回填 charges.customer_id（迁移期一次性）
--    新数据由应用层在 insertPrepaidCharge 时直接写 customer_id
update charges ch
set customer_id = sh.customer_id
from shipments sh
where ch.customer_id is null
  and ch.shipment_id is not null
  and sh.id = ch.shipment_id;

-- 7) charges.settlement_status 一致性约束触发器：审核反审时自动同步
--    （声明式，无业务逻辑改变；只让数据库自身保证一致）
create or replace function charges_sync_settlement() returns trigger as $$
begin
  -- VOID 状态保持
  if new.status = 'VOID' or new.audit_status = 'VOID' then
    new.settlement_status = 'VOID';
  -- 收款全额 → SETTLED
  elsif new.paid_amount >= new.amount then
    new.settlement_status = 'SETTLED';
    new.unpaid_amount = 0;
  -- 部分收款 → PARTIAL
  elsif new.paid_amount > 0 then
    new.settlement_status = 'PARTIAL';
    new.unpaid_amount = new.amount - new.paid_amount;
  else
    new.settlement_status = 'UNSETTLED';
    new.unpaid_amount = new.amount;
  end if;
  return new;
end;
$$ language plpgsql;

drop trigger if exists charges_sync_settlement_tg on charges;
create trigger charges_sync_settlement_tg
  before insert or update of paid_amount, amount, status, audit_status
  on charges
  for each row
  execute function charges_sync_settlement();
